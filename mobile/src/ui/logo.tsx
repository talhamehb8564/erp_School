import { useState } from "react";
import { Text, View } from "react-native";
import { Image } from "expo-image";
import { mediaUrl } from "../lib/media";
import { brandSubtitle, initials } from "../lib/format";
import type { SchoolBrand } from "../lib/types";
import type { Palette } from "../lib/theme";

export function SchoolLogo({
  uri,
  name,
  color,
  size = 56,
}: {
  uri?: string | null;
  name?: string;
  color: string;
  size?: number;
}) {
  const src = mediaUrl(uri);
  const [failed, setFailed] = useState(false);
  const showImage = Boolean(src) && !failed;
  return (
    <View
      accessibilityRole="image"
      accessibilityLabel={name ? `${name} logo` : "School logo"}
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.28,
        backgroundColor: color + "22",
        alignItems: "center",
        justifyContent: "center",
        overflow: "hidden",
      }}
    >
      {showImage ? (
        <Image
          source={{ uri: src }}
          style={{ width: size, height: size }}
          contentFit="cover"
          cachePolicy="memory-disk"
          recyclingKey={src}
          onError={() => setFailed(true)}
          transition={200}
        />
      ) : (
        <Text style={{ color, fontWeight: "800", fontSize: size * 0.32 }}>{initials(name || "ERP")}</Text>
      )}
    </View>
  );
}

export function BrandHeader({
  brand,
  colors,
  kicker,
  subtitle,
  size = 56,
}: {
  brand: SchoolBrand | null;
  colors: Palette;
  kicker?: string;
  subtitle?: string;
  size?: number;
}) {
  const title = brand?.name || "School ERP";
  const sub = subtitle ?? (brand ? brandSubtitle(brand) : undefined);
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 16 }}>
      <SchoolLogo uri={brand?.logoUrl} name={brand?.name} color={colors.brand} size={size} />
      <View style={{ flex: 1, minWidth: 0 }}>
        {kicker ? (
          <Text style={{ color: colors.brand, fontSize: 12, fontWeight: "700", letterSpacing: 1, textTransform: "uppercase" }}>
            {kicker}
          </Text>
        ) : null}
        <Text style={{ color: colors.ink, fontSize: 22, fontWeight: "800" }} numberOfLines={2}>
          {title}
        </Text>
        {sub ? (
          <Text style={{ color: colors.muted, marginTop: 2 }} numberOfLines={2}>
            {sub}
          </Text>
        ) : null}
      </View>
    </View>
  );
}
