import { useEffect, useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { settingsApi } from "../api/services";
import { Btn, Card, Empty, Field, Screen, Title, useAsync } from "../ui/kit";
import { RequireAuth } from "../ui/gate";

export default function Settings() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const s = useAsync(() => settingsApi.get());
  const [bankName, setBankName] = useState("");
  const [accountTitle, setAccountTitle] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [instructions, setInstructions] = useState("");
  useEffect(() => {
    if (!s.data) return;
    setBankName(s.data.bankName || "");
    setAccountTitle(s.data.accountTitle || "");
    setAccountNumber(s.data.accountNumber || "");
    setInstructions(s.data.paymentInstructions || "");
  }, [s.data]);
  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "ACCOUNT_OFFICER"]}>
      <Screen colors={colors}>
        <Title title="Settings" subtitle="Bank details used on fee slips. Logo upload stays on web." colors={colors} />
        {user?.role === "ACCOUNT_OFFICER" ? (
          <Card colors={colors}>
            <Field label="Bank" colors={colors} value={bankName} onChangeText={setBankName} />
            <Field label="Account title" colors={colors} value={accountTitle} onChangeText={setAccountTitle} />
            <Field label="Account number" colors={colors} value={accountNumber} onChangeText={setAccountNumber} />
            <Field label="Instructions" colors={colors} value={instructions} onChangeText={setInstructions} multiline />
            <Btn
              label="Save bank details"
              colors={colors}
              onPress={async () => {
                await settingsApi.save({
                  ...s.data,
                  bankName,
                  accountTitle,
                  accountNumber,
                  paymentInstructions: instructions,
                });
                await s.reload();
              }}
            />
          </Card>
        ) : (
          <Empty title="School admin settings" hint="Branding (logo, colours, school name) is edited on the web console so file uploads stay reliable." colors={colors} />
        )}
        {s.error ? <Text style={{ color: colors.bad }}>{String((s.error as Error).message)}</Text> : null}
      </Screen>
    </RequireAuth>
  );
}
