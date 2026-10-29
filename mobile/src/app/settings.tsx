import { useEffect, useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { pickImage } from "../lib/picker";
import { fileApi, settingsApi, tenantApi } from "../api/services";
import { Btn, Card, Empty, Field, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { BrandHeader } from "../ui/logo";
import { RequireAuth } from "../ui/gate";

export default function Settings() {
  const { user, tenant, brand, refreshTenant } = useSession();
  const { colors } = useAppearance();
  const s = useAsync(() => settingsApi.get());
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [address, setAddress] = useState("");
  const [city, setCity] = useState("");
  const [year, setYear] = useState("");
  const [session, setSession] = useState("");
  const [primary, setPrimary] = useState("");
  const [accent, setAccent] = useState("");
  const [bankName, setBankName] = useState("");
  const [accountTitle, setAccountTitle] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [instructions, setInstructions] = useState("");
  const [msg, setMsg] = useState("");
  const [err, setErr] = useState("");

  useEffect(() => {
    if (!tenant) return;
    setName(tenant.name || "");
    setPhone(tenant.phone || "");
    setEmail(tenant.email || "");
    setAddress(tenant.addressLine || "");
    setCity(tenant.city || "");
    setYear(tenant.academicYear || "");
    setSession(tenant.academicSession || "");
    setPrimary(tenant.primaryColor || "");
    setAccent(tenant.accentColor || "");
  }, [tenant]);

  useEffect(() => {
    if (!s.data) return;
    setBankName(s.data.bankName || "");
    setAccountTitle(s.data.accountTitle || "");
    setAccountNumber(s.data.accountNumber || "");
    setInstructions(s.data.paymentInstructions || "");
  }, [s.data]);

  const admin = user?.role === "SCHOOL_ADMIN";
  const officer = user?.role === "ACCOUNT_OFFICER";

  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "ACCOUNT_OFFICER"]}>
      <Screen colors={colors} onRefresh={() => { void s.reload(); void refreshTenant(); }} refreshing={s.loading}>
        <BrandHeader brand={brand} colors={colors} kicker="Configuration" subtitle="Saved to this school’s database" />
        <Title title="Settings" subtitle="Name, logo and colours come from the tenant record — not a previous school cache." colors={colors} />
        {admin && tenant ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>School identity</Text>
            <Field label="School name" colors={colors} value={name} onChangeText={setName} />
            <Field label="Short code" colors={colors} value={tenant.code} editable={false} />
            <Field label="Phone" colors={colors} value={phone} onChangeText={setPhone} />
            <Field label="Email" colors={colors} value={email} onChangeText={setEmail} autoCapitalize="none" />
            <Field label="Address" colors={colors} value={address} onChangeText={setAddress} />
            <Field label="City" colors={colors} value={city} onChangeText={setCity} />
            <Field label="Academic year" colors={colors} value={year} onChangeText={setYear} />
            <Field label="Academic session" colors={colors} value={session} onChangeText={setSession} />
            <Field label="Primary colour (#RRGGBB)" colors={colors} value={primary} onChangeText={setPrimary} autoCapitalize="none" />
            <Field label="Accent colour (#RRGGBB)" colors={colors} value={accent} onChangeText={setAccent} autoCapitalize="none" />
            <Btn
              label="Upload logo"
              kind="ghost"
              colors={colors}
              onPress={async () => {
                setErr("");
                setMsg("");
                try {
                  const a = await pickImage();
                  if (!a) return;
                  const up = await fileApi.upload(a);
                  await tenantApi.update(tenant.id, { logoUrl: up.url });
                  await refreshTenant();
                  setMsg("Logo saved.");
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
            <Btn
              label="Save school details"
              colors={colors}
              onPress={async () => {
                setErr("");
                setMsg("");
                try {
                  await tenantApi.update(tenant.id, {
                    name: name.trim(),
                    phone,
                    email,
                    addressLine: address,
                    city,
                    academicYear: year,
                    academicSession: session,
                    primaryColor: primary,
                    accentColor: accent,
                  });
                  await refreshTenant();
                  setMsg("School details saved.");
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
          </Card>
        ) : null}
        {officer || admin ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Fee payment details</Text>
            {!officer ? (
              <Text style={{ color: colors.muted, marginBottom: 8 }}>Account Officer can edit bank details. Admin can view them.</Text>
            ) : null}
            <Field label="Bank" colors={colors} value={bankName} onChangeText={setBankName} editable={officer} />
            <Field label="Account title" colors={colors} value={accountTitle} onChangeText={setAccountTitle} editable={officer} />
            <Field label="Account number" colors={colors} value={accountNumber} onChangeText={setAccountNumber} editable={officer} />
            <Field label="Instructions" colors={colors} value={instructions} onChangeText={setInstructions} multiline editable={officer} />
            {officer ? (
              <Btn
                label="Save bank details"
                colors={colors}
                onPress={async () => {
                  setErr("");
                  setMsg("");
                  try {
                    await settingsApi.save({
                      ...s.data,
                      bankName,
                      accountTitle,
                      accountNumber,
                      paymentInstructions: instructions,
                    });
                    await s.reload();
                    setMsg("Bank details saved.");
                  } catch (e) {
                    setErr(friendlyMessage(e));
                  }
                }}
              />
            ) : null}
          </Card>
        ) : (
          <Empty title="Not permitted" colors={colors} />
        )}
        {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
      </Screen>
    </RequireAuth>
  );
}
