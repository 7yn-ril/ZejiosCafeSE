# PayMongo Payments Edge Function

This function keeps the PayMongo secret key out of the Android APK. It supports
both dynamic QR Ph payments for the cafe counter and hosted PayMongo Checkout as
a fallback for cards/e-wallet checkout.

Set the secret in Supabase:

```bash
supabase secrets set PAYMONGO_SECRET_KEY=sk_test_your_key_here
```

Optional settings:

```bash
supabase secrets set PAYMONGO_PUBLIC_KEY=pk_test_your_key_here
supabase secrets set PAYMONGO_BILLING_EMAIL=payments@yourdomain.example
supabase secrets set PAYMONGO_PAYMENT_METHOD_TYPES=card,gcash,paymaya
supabase secrets set PAYMONGO_ORGANIZATION_ID=org_your_org_id
supabase secrets set PAYMONGO_RETURN_BASE_URL=https://YOUR_PROJECT_REF.supabase.co/functions/v1/paymongo-checkout
supabase secrets set PAYMONGO_APP_RETURN_URL=zejioscafe://paymongo/checkout-return
supabase secrets set PAYMONGO_WEBHOOK_SECRET=whsec_your_paymongo_webhook_signing_secret
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=your_supabase_service_role_key
```

`paymongo-checkout` has platform JWT verification disabled so the browser can
load the PayMongo return page. Its POST actions still manually validate the
Android app's Supabase bearer token before creating or verifying checkout
sessions.

Register a PayMongo webhook endpoint for production confirmation:

```text
https://YOUR_PROJECT_REF.supabase.co/functions/v1/paymongo-checkout/webhook
```

Subscribe it to `payment.paid`, `payment.failed`,
`checkout_session.payment.paid`, and `qrph.expired`. The handler verifies the
`Paymongo-Signature` header with `PAYMONGO_WEBHOOK_SECRET` and updates matching
orders by `order_number` or `order_payment_reference` when a pending order row
already exists.

Deploy:

```bash
supabase functions deploy paymongo-checkout --no-verify-jwt
```

The Android app calls this function at:

```text
{SUPABASE_URL}/functions/v1/paymongo-checkout
```
