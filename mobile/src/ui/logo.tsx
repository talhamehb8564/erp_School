import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import { Image } from "expo-image";
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from "react-native-reanimated";
import { mediaUrl } from "../lib/media";
import { brandSubtitle } from "../lib/format";
import type { SchoolBrand } from "../lib/types";
import type { Palette } from "../lib/theme";

const localCrest = require("../../assets/images/icon.png");

export function SchoolLogo({
  uri,
  name,
  color,
  size = 56,
  flip = false,
  variant = "crest",
}: {
  uri?: string | null;
  name?: string;
  color: string;
  size?: number;
  flip?: boolean;
  /** `mark` = rounded square, no colored ring (used inside the experimental coin login). */
  variant?: "crest" | "mark";
}) {
  const src = mediaUrl(uri);
  const [failed, setFailed] = useState(false);
  const showImage = Boolean(src) && !failed;
  const reduce = useReducedMotion();
  const spin = useSharedValue(0);
  useEffect(() => {
    if (!flip || reduce) return;
    spin.value = 0;
    spin.value = withTiming(1, { duration: 680, easing: Easing.out(Easing.cubic) });
  }, [flip, reduce, spin]);
  const spinStyle = useAnimatedStyle(() => ({
    transform: [{ perspective: 800 }, { rotateY: `${spin.value * 360}deg` }],
  }));
  const radius = variant === "mark" ? Math.round(size * 0.22) : size * 0.28;
  const mark = (
    <View
      accessibilityRole="image"
      accessibilityLabel={name ? `${name} logo` : "School logo"}
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        backgroundColor: variant === "mark" ? "transparent" : color + "22",
        alignItems: "center",
        justifyContent: "center",
        overflow: "hidden",
      }}
    >
      {showImage ? (
        <Image
          source={{ uri: src }}
          style={{ width: size, height: size }}
          contentFit={variant === "mark" ? "contain" : "cover"}
          cachePolicy="memory-disk"
          recyclingKey={src}
          onError={() => setFailed(true)}
          transition={200}
        />
      ) : (
        <Image
          source={localCrest}
          style={{ width: size, height: size }}
          contentFit="cover"
          accessibilityLabel={name ? `${name} logo` : "School logo"}
        />
      )}
    </View>
  );
  if (!flip || reduce) return mark;
  return <Animated.View style={spinStyle}>{mark}</Animated.View>;
}

export function BrandHeader({
  brand,
  colors,
  kicker,
  subtitle,
  size = 56,
  flip = false,
}: {
  brand: SchoolBrand | null;
  colors: Palette;
  kicker?: string;
  subtitle?: string;
  size?: number;
  flip?: boolean;
}) {
  const title = brand?.name || "School Portal";
  const sub = subtitle ?? (brand ? brandSubtitle(brand) : undefined);
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 16 }}>
      <SchoolLogo uri={brand?.logoUrl} name={brand?.name} color={colors.brand} size={size} flip={flip} />
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
