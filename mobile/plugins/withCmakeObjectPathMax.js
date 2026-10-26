/**
 * Windows Ninja 1.10 (bundled with Android SDK CMake 3.22.1) rejects object
 * paths over 260 characters. RNGH 3 codegen paths exceed that.
 *
 * Pair this with expo-build-properties android.cmakeVersion 3.31.6 (Ninja 1.12+)
 * and Windows LongPathsEnabled. CMAKE_OBJECT_PATH_MAX=1024 tells CMake that
 * the native tool can handle long names once Ninja is new enough.
 */
const { withAppBuildGradle } = require("expo/config-plugins");

const FLAG = "-DCMAKE_OBJECT_PATH_MAX=1024";

function withCmakeObjectPathMax(config) {
  return withAppBuildGradle(config, (mod) => {
    let src = mod.modResults.contents;
    if (src.includes("CMAKE_OBJECT_PATH_MAX")) return mod;

    if (mod.modResults.language === "kts") {
      const block = `
        externalNativeBuild {
            cmake {
                arguments += listOf("${FLAG}")
            }
        }
`;
      if (!/defaultConfig\s*\{/.test(src)) return mod;
      src = src.replace(/defaultConfig\s*\{/, (m) => `${m}${block}`);
    } else {
      const block = `
        externalNativeBuild {
            cmake {
                arguments "${FLAG}"
            }
        }
`;
      if (!/defaultConfig\s*\{/.test(src)) return mod;
      src = src.replace(/defaultConfig\s*\{/, (m) => `${m}${block}`);
    }
    mod.modResults.contents = src;
    return mod;
  });
}

module.exports = withCmakeObjectPathMax;
