import * as ImagePicker from "expo-image-picker";

export type PickedFile = { uri: string; name: string; type: string };

function toFile(a: ImagePicker.ImagePickerAsset): PickedFile {
  return {
    uri: a.uri,
    name: a.fileName || `upload-${Date.now()}.jpg`,
    type: a.mimeType || "image/jpeg",
  };
}

async function ensureLibrary() {
  const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!perm.granted) throw new Error("Photo library permission is required to upload files.");
}

export async function pickImage() {
  await ensureLibrary();
  const result = await ImagePicker.launchImageLibraryAsync({
    quality: 0.7,
    allowsMultipleSelection: false,
  });
  if (result.canceled || !result.assets?.[0]) return null;
  return toFile(result.assets[0]);
}

export async function pickImages(limit = 6) {
  await ensureLibrary();
  const result = await ImagePicker.launchImageLibraryAsync({
    quality: 0.7,
    allowsMultipleSelection: true,
    selectionLimit: limit,
  });
  if (result.canceled || !result.assets?.length) return [];
  return result.assets.map(toFile);
}
