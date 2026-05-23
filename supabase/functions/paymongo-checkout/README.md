# PayMongo Checkout Edge Function

This function keeps the PayMongo secret key out of the Android APK.

Set the secret in Supabase:

```bash
supabase secrets set PAYMONGO_SECRET_KEY=sk_test_your_key_here
```

Optional settings:

```bash
supabase secrets set PAYMONGO_PAYMENT_METHOD_TYPES=card,gcash,paymaya
supabase secrets set PAYMONGO_ORGANIZATION_ID=org_your_org_id
supabase secrets set PAYMONGO_RETURN_BASE_URL=https://YOUR_PROJECT_REF.supabase.co/functions/v1/paymongo-checkout
supabase secrets set PAYMONGO_APP_RETURN_URL=zejioscafe://paymongo/checkout-return
```

`paymongo-checkout` has platform JWT verification disabled so the browser can
load the PayMongo return page. Its POST actions still manually validate the
Android app's Supabase bearer token before creating or verifying checkout
sessions.

Deploy:

```bash
supabase functions deploy paymongo-checkout --no-verify-jwt
```

The Android app calls this function at:

```text
{SUPABASE_URL}/functions/v1/paymongo-checkout
```
