type PayMongoAction = "create" | "retrieve" | "create_qrph" | "retrieve_payment_intent";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, paymongo-signature",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
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
    if (isPayMongoWebhookRequest(request)) {
      return await handlePayMongoWebhook(request);
    }

    const authError = await postAuthenticationError(request);
    if (authError) {
      return json({ error: authError }, 401);
    }

    const body = await request.json();
    const action = String(body.action ?? "") as PayMongoAction;

    if (action === "create") {
      return json(await createCheckoutSession(body, request));
    }

    if (action === "retrieve") {
      return json(await retrieveCheckoutSession(body));
    }

    if (action === "create_qrph") {
      return json(await createQrPhPayment(body));
    }

    if (action === "retrieve_payment_intent") {
      return json(await retrievePaymentIntent(body));
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
    payment_method_used: paymentMethodUsed(attributes),
    billing_name: billingName(attributes),
  };
}

async function createQrPhPayment(body: Record<string, unknown>) {
  const orderNumber = cleanText(body.order_number, "POS order");
  const customerName = cleanText(body.customer_name, "Walk-in Customer");
  const amountCentavos = numberValue(body.amount_centavos);
  if (amountCentavos < 100) {
    throw new Error("QR Ph amount must be at least PHP 1.00.");
  }

  const lineSummary = checkoutLineSummary(body.line_items);
  const metadata = {
    order_number: orderNumber,
    customer_name: customerName,
    payment_channel: "qrph",
  };

  const intentResponse = await paymongoFetch("/v1/payment_intents", {
    method: "POST",
    body: JSON.stringify({
      data: {
        attributes: {
          amount: amountCentavos,
          currency: "PHP",
          payment_method_allowed: ["qrph"],
          description: `${orderNumber} - ${customerName}${lineSummary ? ` - ${lineSummary}` : ""}`,
          metadata,
        },
      },
    }),
  });

  const intent = nestedObject(intentResponse.data);
  const intentAttributes = nestedObject(intent?.attributes);
  const paymentIntentId = stringValue(intent?.id);
  const clientKey = stringValue(intentAttributes?.client_key);
  if (!paymentIntentId || !clientKey) {
    throw new Error("PayMongo did not return a Payment Intent client key.");
  }

  const methodResponse = await paymongoFetch(
    "/v1/payment_methods",
    {
      method: "POST",
      body: JSON.stringify({
        data: {
          attributes: {
            type: "qrph",
            billing: {
              name: customerName,
              email: billingEmail(),
            },
          },
        },
      }),
    },
    paymongoClientApiKey(),
  );
  const paymentMethod = nestedObject(methodResponse.data);
  const paymentMethodId = stringValue(paymentMethod?.id);
  if (!paymentMethodId) {
    throw new Error("PayMongo did not return a QR Ph Payment Method id.");
  }

  const attachedResponse = await paymongoFetch(
    `/v1/payment_intents/${encodeURIComponent(paymentIntentId)}/attach`,
    {
      method: "POST",
      body: JSON.stringify({
        data: {
          attributes: {
            payment_method: paymentMethodId,
            client_key: clientKey,
          },
        },
      }),
    },
    paymongoClientApiKey(),
  );

  const attachedIntent = nestedObject(attachedResponse.data);
  const attachedAttributes = nestedObject(attachedIntent?.attributes);
  const qrImageUrl = qrCodeImageUrl(attachedAttributes);
  if (!qrImageUrl) {
    throw new Error("PayMongo did not return a QR Ph image.");
  }

  return {
    id: stringValue(attachedIntent?.id) ?? paymentIntentId,
    payment_intent_id: paymentIntentId,
    payment_method_id: paymentMethodId,
    qr_image_url: qrImageUrl,
    test_url: qrTestUrl(attachedAttributes),
    reference_number: orderNumber,
    status: checkoutStatus(attachedAttributes),
  };
}

async function retrievePaymentIntent(body: Record<string, unknown>) {
  const paymentIntentId = cleanText(body.payment_intent_id, "");
  if (!paymentIntentId) {
    throw new Error("payment_intent_id is required.");
  }

  const paymongo = await paymongoFetch(
    `/v1/payment_intents/${encodeURIComponent(paymentIntentId)}`,
    { method: "GET" },
  );

  const data = nestedObject(paymongo.data);
  const attributes = nestedObject(data?.attributes);
  const status = checkoutStatus(attributes);
  const paid = isPaid(attributes, status);

  return {
    id: stringValue(data?.id) ?? paymentIntentId,
    status,
    paid,
    payment_reference: paymentReference(data, attributes) ?? paymentIntentId,
    payment_method_used: paymentMethodUsed(attributes) ?? "qrph",
    billing_name: billingName(attributes),
  };
}

// Returns the customer name that was entered inside the PayMongo
// hosted checkout. The Android app uses this to overwrite the order's
// customer name on save — when the cashier didn't type a name in the
// POS form, the buyer's PayMongo billing name is the better source of
// truth on the receipt and in reports.
function billingName(attributes: Record<string, unknown> | undefined) {
  const firstPayment = firstNestedAttributes(attributes?.payments);
  const billing = nestedObject(firstPayment?.billing);
  return stringValue(billing?.name);
}

// Returns the actual instrument the customer paid with (gcash, maya,
// card, ...), normalised to the lowercase tokens the Android app stores
// in order_payment_method. Returns null if PayMongo did not surface a
// resolvable method, in which case the client falls back to 'paymongo'.
function paymentMethodUsed(attributes: Record<string, unknown> | undefined) {
  const firstPayment = firstNestedAttributes(attributes?.payments);
  // PayMongo exposes the method as either source.type (gcash, paymaya,
  // grab_pay, ...) or payment_method_used on newer responses. Check both.
  const rawSource = stringValue(nestedObject(firstPayment?.source)?.type)
    ?? stringValue(firstPayment?.payment_method_used);
  if (!rawSource) return null;
  const normalised = rawSource.trim().toLowerCase();
  if (isQrPhPaymentMethod(normalised)) return "qrph";
  return normalised;
}

async function handlePayMongoWebhook(request: Request) {
  const rawBody = await request.text();
  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(rawBody);
  } catch {
    return json({ error: "Invalid webhook JSON." }, 400);
  }

  const eventAttributes = nestedAttributes(payload.data);
  const eventResource = nestedObject(eventAttributes?.data);
  const livemode = Boolean(nestedObject(eventResource?.attributes)?.livemode ?? eventAttributes?.livemode);
  const signatureOk = await verifyPayMongoSignature(
    request.headers.get("Paymongo-Signature"),
    rawBody,
    livemode,
  );
  if (!signatureOk) {
    return json({ error: "Webhook signature verification failed." }, 401);
  }

  try {
    await applyWebhookPaymentUpdate(payload);
  } catch (error) {
    console.error("PayMongo webhook processing failed:", error instanceof Error ? error.message : error);
    // PayMongo retries non-2xx responses. After signature verification,
    // acknowledge and let operators inspect logs instead of causing a retry loop.
  }

  return json({ received: true });
}

async function applyWebhookPaymentUpdate(payload: Record<string, unknown>) {
  const eventAttributes = nestedAttributes(payload.data);
  const eventType = stringValue(eventAttributes?.type);
  const eventData = nestedObject(eventAttributes?.data);
  const eventResourceType = stringValue(eventData?.type);
  const resourceAttributes = nestedObject(eventData?.attributes);
  if (!eventType || !eventData || !resourceAttributes) return;

  const paymentStatus = webhookPaymentStatus(eventType, resourceAttributes);
  if (!paymentStatus) return;

  let paymentIntentId = stringValue(resourceAttributes.payment_intent_id)
    ?? stringValue(nestedObject(resourceAttributes.payment_intent)?.id);
  const checkoutSessionId = eventResourceType === "checkout_session" ? stringValue(eventData.id) : undefined;
  let orderNumber = stringValue(nestedObject(resourceAttributes.metadata)?.order_number)
    ?? stringValue(resourceAttributes.reference_number);
  let paymentMethod = webhookPaymentMethod(eventType, resourceAttributes);
  let paymentReference = webhookPaymentReference(eventData, resourceAttributes)
    ?? paymentIntentId
    ?? checkoutSessionId;

  if ((!orderNumber || !paymentMethod) && paymentIntentId) {
    const intent = await fetchPaymentIntentForWebhook(paymentIntentId).catch((error) => {
      console.error("Unable to enrich webhook Payment Intent:", error instanceof Error ? error.message : error);
      return null;
    });
    const intentAttributes = nestedObject(intent?.attributes);
    orderNumber = orderNumber
      ?? stringValue(nestedObject(intentAttributes?.metadata)?.order_number)
      ?? stringValue(intentAttributes?.reference_number);
    paymentMethod = paymentMethod ?? paymentMethodUsed(intentAttributes) ?? "qrph";
    paymentReference = paymentReference ?? stringValue(intent?.id);
  }

  if (eventType === "qrph.expired" && !paymentIntentId) {
    paymentIntentId = stringValue(resourceAttributes.payment_intent_id);
  }

  await updateOrderPaymentFromWebhook({
    orderNumber,
    paymentIntentId,
    checkoutSessionId,
    paymentReference,
    paymentMethod,
    paymentStatus,
  });
}

async function fetchPaymentIntentForWebhook(paymentIntentId: string) {
  const response = await paymongoFetch(
    `/v1/payment_intents/${encodeURIComponent(paymentIntentId)}`,
    { method: "GET" },
  );
  return nestedObject(response.data);
}

async function updateOrderPaymentFromWebhook(update: {
  orderNumber?: string;
  paymentIntentId?: string;
  checkoutSessionId?: string;
  paymentReference?: string;
  paymentMethod?: string;
  paymentStatus: string;
}) {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim()?.replace(/\/$/, "");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceRoleKey) {
    throw new Error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required for PayMongo webhooks.");
  }

  const body: Record<string, unknown> = {
    order_payment_provider: "paymongo",
    order_payment_status: update.paymentStatus,
  };
  if (update.paymentReference) body.order_payment_reference = update.paymentReference;
  if (update.paymentStatus === "paid") {
    if (update.paymentMethod) body.order_payment_method = normalisePaymentMethod(update.paymentMethod);
    body.order_status = "preparing";
  }

  const filters = [
    update.orderNumber ? { column: "order_number", value: update.orderNumber } : null,
    update.paymentIntentId ? { column: "order_payment_reference", value: update.paymentIntentId } : null,
    update.checkoutSessionId ? { column: "order_payment_reference", value: update.checkoutSessionId } : null,
  ].filter(Boolean) as Array<{ column: string; value: string }>;

  for (const filter of filters) {
    const url = `${supabaseUrl}/rest/v1/orders?${filter.column}=eq.${encodeURIComponent(filter.value)}`;
    const response = await fetch(url, {
      method: "PATCH",
      headers: {
        "accept": "application/json",
        "apikey": serviceRoleKey,
        "authorization": `Bearer ${serviceRoleKey}`,
        "content-type": "application/json",
        "prefer": "return=minimal",
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      throw new Error(`Supabase order payment update failed: HTTP ${response.status} ${detail}`);
    }
  }
}

async function verifyPayMongoSignature(
  header: string | null,
  rawBody: string,
  livemode: boolean,
) {
  const secret = Deno.env.get("PAYMONGO_WEBHOOK_SECRET")?.trim();
  if (!secret) {
    throw new Error("PAYMONGO_WEBHOOK_SECRET is not configured.");
  }
  const parts = parseSignatureHeader(header);
  const timestamp = parts.t;
  if (!timestamp) return false;

  const toleranceSeconds = numberValue(Deno.env.get("PAYMONGO_WEBHOOK_TOLERANCE_SECONDS"), 600);
  const timestampSeconds = Number.parseInt(timestamp, 10);
  if (
    Number.isFinite(timestampSeconds) &&
    toleranceSeconds > 0 &&
    Math.abs(Math.floor(Date.now() / 1000) - timestampSeconds) > toleranceSeconds
  ) {
    return false;
  }

  const expected = await hmacSha256Hex(secret, `${timestamp}.${rawBody}`);
  const candidate = livemode ? parts.li : parts.te;
  return timingSafeEqual(expected, candidate) ||
    timingSafeEqual(expected, parts.te) ||
    timingSafeEqual(expected, parts.li);
}

async function hmacSha256Hex(secret: string, message: string) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(message));
  return Array.from(new Uint8Array(signature))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function parseSignatureHeader(header: string | null) {
  const parts: Record<string, string | undefined> = {};
  header?.split(",").forEach((segment) => {
    const [rawKey, ...rawValue] = segment.split("=");
    const key = rawKey?.trim();
    if (key) parts[key] = rawValue.join("=").trim();
  });
  return parts;
}

function timingSafeEqual(expected: string | undefined, candidate: string | undefined) {
  if (!expected || !candidate || expected.length !== candidate.length) return false;
  let result = 0;
  for (let index = 0; index < expected.length; index += 1) {
    result |= expected.charCodeAt(index) ^ candidate.charCodeAt(index);
  }
  return result === 0;
}

function webhookPaymentStatus(eventType: string, attributes: Record<string, unknown>) {
  if (eventType === "payment.paid" || eventType === "checkout_session.payment.paid") return "paid";
  if (eventType === "payment.failed") return "failed";
  if (eventType === "qrph.expired") return "expired";
  const status = stringValue(attributes.status)?.toLowerCase();
  if (status && ["paid", "succeeded", "completed"].includes(status)) return "paid";
  if (status && ["failed", "expired"].includes(status)) return status;
  return null;
}

function webhookPaymentMethod(eventType: string, attributes: Record<string, unknown>) {
  if (eventType === "qrph.expired") return "qrph";
  return paymentMethodUsed(attributes)
    ?? stringValue(nestedObject(attributes.source)?.type)
    ?? stringValue(attributes.payment_method_used);
}

function webhookPaymentReference(
  eventData: Record<string, unknown>,
  attributes: Record<string, unknown>,
) {
  return stringValue(eventData.id)
    ?? stringValue(firstNestedObject(attributes.payments)?.id)
    ?? stringValue(nestedObject(attributes.payment_intent)?.id)
    ?? stringValue(attributes.reference_number);
}

function normalisePaymentMethod(method: string) {
  const normalised = method.trim().toLowerCase();
  if (isQrPhPaymentMethod(normalised)) return "qrph";
  return normalised;
}

function isQrPhPaymentMethod(method: string) {
  return ["qrph", "qr_ph", "gcash", "maya", "paymaya"].includes(method);
}

function paymongoClientApiKey() {
  return Deno.env.get("PAYMONGO_PUBLIC_KEY")?.trim() ||
    Deno.env.get("PAYMONGO_SECRET_KEY")?.trim();
}

function billingEmail() {
  return Deno.env.get("PAYMONGO_BILLING_EMAIL")?.trim() ||
    "payments@example.com";
}

async function paymongoFetch(path: string, init: RequestInit, apiKey?: string) {
  const resolvedApiKey = apiKey?.trim() || Deno.env.get("PAYMONGO_SECRET_KEY")?.trim();
  if (!resolvedApiKey) {
    throw new Error("PAYMONGO_SECRET_KEY is not configured in Supabase Edge Function secrets.");
  }

  const apiBase = Deno.env.get("PAYMONGO_API_BASE")?.trim() || "https://api.paymongo.com";
  const organizationId = Deno.env.get("PAYMONGO_ORGANIZATION_ID")?.trim();
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      "accept": "application/json",
      "content-type": "application/json",
      "authorization": `Basic ${btoa(`${resolvedApiKey}:`)}`,
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

function checkoutLineSummary(value: unknown) {
  return Array.isArray(value)
    ? value
        .map((item) => {
          const line = item as Record<string, unknown>;
          const name = cleanText(line.name, "Item");
          const quantity = Math.max(1, numberValue(line.quantity, 1));
          return `${quantity} x ${name}`;
        })
        .join("; ")
    : "";
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

function qrCodeImageUrl(attributes: Record<string, unknown> | undefined) {
  return stringValue(nestedObject(nestedObject(attributes?.next_action)?.code)?.image_url);
}

function qrTestUrl(attributes: Record<string, unknown> | undefined) {
  const nextAction = nestedObject(attributes?.next_action);
  return stringValue(nestedObject(nextAction?.code)?.test_url)
    ?? stringValue(nestedObject(nextAction?.redirect)?.url);
}

function isPayMongoWebhookRequest(request: Request) {
  const url = new URL(request.url);
  return url.searchParams.get("paymongo_webhook") === "1" ||
    url.pathname.replace(/\/$/, "").endsWith("/webhook");
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
