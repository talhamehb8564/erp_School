import * as ImagePicker from "expo-image-picker";

export async function pickImage() {
  const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!perm.granted) throw new Error("Photo library permission is required to upload files.");
  const result = await ImagePicker.launchImageLibraryAsync({
    quality: 0.7,
  });
  if (result.canceled || !result.assets?.[0]) return null;
  const a = result.assets[0];
  return {
    uri: a.uri,
    name: a.fileName || `upload-${Date.now()}.jpg`,
    type: a.mimeType || "image/jpeg",
  };
}
