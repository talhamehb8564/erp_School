import { useState } from "react";
import { Text } from "react-native";
import { router } from "expo-router";
import { useAppearance } from "../lib/appearance";
import { authApi } from "../api/services";
import { Btn, Field, Screen, Title, friendlyMessage } from "../ui/kit";
import { RequireAuth } from "../ui/gate";

export default function Password() {
  const { colors } = useAppearance();
  const [currentPassword, setCurrent] = useState("");
  const [newPassword, setNew] = useState("");
  const [newUsername, setUser] = useState("");
  const [msg, setMsg] = useState("");
  const [err, setErr] = useState("");
  return (
    <RequireAuth>
      <Screen colors={colors}>
        <Title title="Account" subtitle="Username-only, password-only, or both. Hash is updated on the server." colors={colors} />
        <Field label="Current password" colors={colors} secureTextEntry value={currentPassword} onChangeText={setCurrent} />
        <Field label="New username (optional)" colors={colors} autoCapitalize="none" value={newUsername} onChangeText={setUser} />
        <Field label="New password (optional)" colors={colors} secureTextEntry value={newPassword} onChangeText={setNew} />
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
        {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
        <Btn
          label="Save"
          colors={colors}
          onPress={async () => {
            setErr("");
            setMsg("");
            try {
              await authApi.changePassword(currentPassword, newPassword || undefined, newUsername || undefined);
              setMsg("Saved. Use the new credentials next time you sign in.");
            } catch (e) {
              setErr(friendlyMessage(e));
            }
          }}
        />
        <Btn label="Back" kind="ghost" colors={colors} onPress={() => router.back()} />
      </Screen>
    </RequireAuth>
  );
}
