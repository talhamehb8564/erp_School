import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

const memory = new Map<string, string>();

export async function storeGet(key: string) {
  try {
    if (Platform.OS === "web") return memory.get(key) ?? null;
    return await SecureStore.getItemAsync(key);
  } catch {
    return memory.get(key) ?? null;
  }
}

export async function storeSet(key: string, value: string) {
  memory.set(key, value);
  if (Platform.OS === "web") return;
  try {
    await SecureStore.setItemAsync(key, value);
  } catch {
    /* memory fallback */
  }
}

export async function storeDel(key: string) {
  memory.delete(key);
  if (Platform.OS === "web") return;
  try {
    await SecureStore.deleteItemAsync(key);
  } catch {
    /* ignore */
  }
}
