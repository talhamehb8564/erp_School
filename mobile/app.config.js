const fs = require("fs");
const path = require("path");

function loadSchool() {
  const file = process.env.SCHOOL_BRANDING || path.join(__dirname, "branding", "school.json");
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

module.exports = () => {
  const school = loadSchool();
  const appName = school.appName || `${String(school.name || "School").trim()} Portal`;
  const color = school.primaryColor || "#111111";
  const splashBg = school.splashBackgroundColor || "#FFFFFF";
  const iconBg = school.iconBackgroundColor || splashBg;
  const icon = school.icon || "./branding/assets/icon.png";
  const splash = school.splash || "./branding/assets/splash-icon.png";
  const foreground = school.adaptiveForeground || icon;
  return {
    expo: {
      name: appName,
      slug: "school-erp",
      version: "1.0.0",
      orientation: "portrait",
      icon,
      scheme: "erpschool",
      userInterfaceStyle: "automatic",
      ios: {
        supportsTablet: true,
        bundleIdentifier: school.iosBundle || "com.devzard.schoolerp",
        icon,
      },
      android: {
        package: school.androidPackage || "com.devzard.schoolerp",
        versionCode: 1,
        adaptiveIcon: {
          backgroundColor: iconBg,
          foregroundImage: foreground,
        },
        predictiveBackGestureEnabled: false,
      },
      web: {
        output: "single",
        favicon: school.favicon || "./assets/images/favicon.png",
      },
      plugins: [
        "expo-router",
        "./plugins/withCmakeObjectPathMax.js",
        [
          "expo-build-properties",
          {
            android: {
              usesCleartextTraffic: true,
              cmakeVersion: "3.31.6",
            },
          },
        ],
        "expo-secure-store",
        [
          "expo-image-picker",
          {
            photosPermission: `${appName} needs photo access to upload homework and payment slips.`,
          },
        ],
        [
          "expo-splash-screen",
          {
            backgroundColor: splashBg,
            image: splash,
            imageWidth: 220,
          },
        ],
        [
          "expo-notifications",
          {
            color,
          },
        ],
      ],
      experiments: {
        typedRoutes: true,
      },
      extra: {
        apiUrl: process.env.EXPO_PUBLIC_API_URL || "",
        school: {
          name: school.name,
          appName,
          code: school.code || "",
          primaryColor: color,
          accentColor: school.accentColor || "",
          tagline: school.tagline || "Portal",
        },
      },
    },
  };
};
