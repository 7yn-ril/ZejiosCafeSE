type CheckoutAction = "create" | "retrieve";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method === "GET") {
    const url = new URL(request.url);
    if (url.searchParams.get("paymongo_return") === "1") {
      return appReturnPage(url);
    }
    return json({ error: "Method not allowed." }, 405);
  }

  if (request.method !== "POST") {
    return json({ error: "Method not allowed." }, 405);
  }

  try {
    const authError = await postAuthenticationError(request);
    if (authError) {
      return json({ error: authError }, 401);
    }

    const body = await request.json();
    const action = String(body.action ?? "") as CheckoutAction;

    if (action === "create") {
      return json(await createCheckoutSession(body, request));
    }

    if (action === "retrieve") {
      return json(await retrieveCheckoutSession(body));
    }

    return json({ error: "Unsupported PayMongo checkout action." }, 400);
  } catch (error) {
    const message = error instanceof Error ? error.message : "PayMongo checkout failed.";
    console.error("PayMongo checkout function failed:", message);
    return json({ error: message }, 500);
  }
});

async function createCheckoutSession(body: Record<string, unknown>, request: Request) {
  const orderNumber = cleanText(body.order_number, "POS order");
  const customerName = cleanText(body.customer_name, "Walk-in Customer");
  const amountCentavos = numberValue(body.amount_centavos);
  if (amountCentavos < 100) {
    throw new Error("PayMongo checkout amount must be at least PHP 1.00.");
  }

  const lineSummary = Array.isArray(body.line_items)
    ? body.line_items
        .map((item) => {
          const line = item as Record<string, unknown>;
          const name = cleanText(line.name, "Item");
          const quantity = Math.max(1, numberValue(line.quantity, 1));
          return `${quantity} x ${name}`;
        })
        .join("; ")
    : "";

  const successUrl = Deno.env.get("PAYMONGO_SUCCESS_URL")?.trim() ||
    checkoutReturnUrl(request, "success", orderNumber);
  const cancelUrl = Deno.env.get("PAYMONGO_CANCEL_URL")?.trim() ||
    checkoutReturnUrl(request, "cancel", orderNumber);

  const attributes: Record<string, unknown> = {
    send_email_receipt: false,
    show_description: true,
    show_line_items: true,
    description: `${orderNumber} - ${customerName}${lineSummary ? ` - ${lineSummary}` : ""}`,
    reference_number: orderNumber,
    metadata: {
      order_number: orderNumber,
      customer_name: customerName,
    },
    line_items: [
      {
        name: `Zejios Cafe ${orderNumber}`,
        amount: amountCentavos,
        currency: "PHP",
        quantity: 1,
        description: lineSummary || "Cafe order",
      },
    ],
    payment_method_types: paymentMethodTypes(),
  };

  attributes.success_url = successUrl;
  attributes.cancel_url = cancelUrl;

  const paymongo = await paymongoFetch("/v1/checkout_sessions", {
    method: "POST",
    body: JSON.stringify({ data: { attributes } }),
  });

  const data = paymongo.data as Record<string, unknown> | undefined;
  const responseAttributes = data?.attributes as Record<string, unknown> | undefined;
  const checkoutUrl = stringValue(responseAttributes?.checkout_url) ?? stringValue(responseAttributes?.url);
  const id = stringValue(data?.id);

  if (!id || !checkoutUrl) {
    throw new Error("PayMongo did not return a checkout URL.");
  }

  return {
    id,
    checkout_url: checkoutUrl,
    reference_number: stringValue(responseAttributes?.reference_number) ?? orderNumber,
    status: stringValue(responseAttributes?.status),
  };
}

async function retrieveCheckoutSession(body: Record<string, unknown>) {
  const checkoutSessionId = cleanText(body.checkout_session_id, "");
  if (!checkoutSessionId) {
    throw new Error("checkout_session_id is required.");
  }

  const paymongo = await paymongoFetch(
    `/v1/checkout_sessions/${encodeURIComponent(checkoutSessionId)}`,
    { method: "GET" },
  );

  const data = paymongo.data as Record<string, unknown> | undefined;
  const attributes = data?.attributes as Record<string, unknown> | undefined;
  const status = checkoutStatus(attributes);
  const paid = isPaid(attributes, status);

  return {
    id: stringValue(data?.id) ?? checkoutSessionId,
    status,
    paid,
    payment_reference: paymentReference(data, attributes) ?? checkoutSessionId,
  };
}

async function paymongoFetch(path: string, init: RequestInit) {
  const secretKey = Deno.env.get("PAYMONGO_SECRET_KEY")?.trim();
  if (!secretKey) {
    throw new Error("PAYMONGO_SECRET_KEY is not configured in Supabase Edge Function secrets.");
  }

  const apiBase = Deno.env.get("PAYMONGO_API_BASE")?.trim() || "https://api.paymongo.com";
  const organizationId = Deno.env.get("PAYMONGO_ORGANIZATION_ID")?.trim();
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      "accept": "application/json",
      "content-type": "application/json",
      "authorization": `Basic ${btoa(`${secretKey}:`)}`,
      ...(organizationId ? { "Organization-Id": organizationId } : {}),
      ...(init.headers ?? {}),
    },
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = paymongoErrorMessage(payload) ?? `PayMongo returned HTTP ${response.status}.`;
    throw new Error(detail);
  }
  return payload as Record<string, unknown>;
}

async function postAuthenticationError(request: Request) {
  const authorization = request.headers.get("authorization")?.trim();
  if (!authorization?.toLowerCase().startsWith("bearer ")) {
    return "Supabase user session is required for PayMongo checkout requests.";
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const apiKey = Deno.env.get("SUPABASE_ANON_KEY")?.trim() ||
    request.headers.get("apikey")?.trim();
  if (!supabaseUrl || !apiKey) {
    return "Supabase auth environment is not configured for PayMongo checkout.";
  }

  const response = await fetch(`${supabaseUrl.replace(/\/$/, "")}/auth/v1/user`, {
    headers: {
      "accept": "application/json",
      "apikey": apiKey,
      "authorization": authorization,
    },
  });

  if (!response.ok) {
    return "Supabase rejected the current app session. Reopen the app and try again.";
  }

  return null;
}

function paymentMethodTypes() {
  return (Deno.env.get("PAYMONGO_PAYMENT_METHOD_TYPES") ?? "card,gcash,paymaya")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
}

function checkoutStatus(attributes: Record<string, unknown> | undefined) {
  const directStatus = stringValue(attributes?.status);
  if (directStatus) return directStatus;

  const paymentIntent = nestedAttributes(attributes?.payment_intent);
  const paymentIntentStatus = stringValue(paymentIntent?.status);
  if (paymentIntentStatus) return paymentIntentStatus;

  const firstPayment = firstNestedAttributes(attributes?.payments);
  return stringValue(firstPayment?.status);
}

function isPaid(attributes: Record<string, unknown> | undefined, status?: string) {
  const normalizedStatus = status?.toLowerCase();
  if (normalizedStatus && ["paid", "succeeded", "completed"].includes(normalizedStatus)) {
    return true;
  }

  const paymentIntent = nestedAttributes(attributes?.payment_intent);
  const paymentIntentStatus = stringValue(paymentIntent?.status)?.toLowerCase();
  if (paymentIntentStatus && ["paid", "succeeded", "completed"].includes(paymentIntentStatus)) {
    return true;
  }

  const firstPayment = firstNestedAttributes(attributes?.payments);
  const paymentStatus = stringValue(firstPayment?.status)?.toLowerCase();
  return Boolean(paymentStatus && ["paid", "succeeded", "completed"].includes(paymentStatus));
}

function paymentReference(
  data: Record<string, unknown> | undefined,
  attributes: Record<string, unknown> | undefined,
) {
  const firstPayment = firstNestedObject(attributes?.payments);
  return stringValue(firstPayment?.id) ??
    stringValue(nestedObject(attributes?.payment_intent)?.id) ??
    stringValue(attributes?.reference_number) ??
    stringValue(data?.id);
}

function checkoutReturnUrl(request: Request, result: "success" | "cancel", orderNumber: string) {
  const configuredBase = Deno.env.get("PAYMONGO_RETURN_BASE_URL")?.trim();
  const requestUrl = new URL(request.url);
  const publicBase = `${requestUrl.origin}/functions/v1/paymongo-checkout`;
  const url = new URL(configuredBase || publicBase);
  url.searchParams.set("paymongo_return", "1");
  url.searchParams.set("result", result);
  url.searchParams.set("order_number", orderNumber);
  return url.toString();
}

function appReturnPage(url: URL) {
  const result = url.searchParams.get("result") || "success";
  const orderNumber = url.searchParams.get("order_number") || "";
  const appUrl = new URL(
    Deno.env.get("PAYMONGO_APP_RETURN_URL")?.trim() ||
      "zejioscafe://paymongo/checkout-return",
  );
  appUrl.searchParams.set("result", result);
  if (orderNumber) appUrl.searchParams.set("order_number", orderNumber);

  const appUrlText = appUrl.toString();
  return new Response(
    `Returning to Zejios Cafe: ${appUrlText}`,
    {
      status: 302,
      headers: {
        ...corsHeaders,
        "location": appUrlText,
        "content-type": "text/plain; charset=utf-8",
      },
    },
  );
}

function paymongoErrorMessage(payload: Record<string, unknown>) {
  const errors = payload.errors;
  if (Array.isArray(errors) && errors.length > 0) {
    const first = errors[0] as Record<string, unknown>;
    return stringValue(first.detail) ?? stringValue(first.message) ?? stringValue(first.code);
  }
  return stringValue(payload.error) ?? stringValue(payload.message);
}

function nestedObject(value: unknown) {
  if (!value || typeof value !== "object") return undefined;
  return value as Record<string, unknown>;
}

function nestedAttributes(value: unknown) {
  return nestedObject(nestedObject(value)?.attributes);
}

function firstNestedObject(value: unknown) {
  if (!Array.isArray(value) || value.length === 0) return undefined;
  return nestedObject(value[0]);
}

function firstNestedAttributes(value: unknown) {
  return nestedAttributes(firstNestedObject(value));
}

function numberValue(value: unknown, fallback = 0) {
  if (typeof value === "number" && Number.isFinite(value)) return Math.round(value);
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function stringValue(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function cleanText(value: unknown, fallback: string) {
  return stringValue(value)?.slice(0, 500) ?? fallback;
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "content-type": "application/json",
    },
  });
}
