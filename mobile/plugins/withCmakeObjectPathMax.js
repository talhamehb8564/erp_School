/**
 * Windows Ninja 1.10 (bundled with Android SDK CMake 3.22.1) rejects object
 * paths over 260 characters. RNGH 3 codegen paths exceed that.
 *
 * expo-build-properties writes android.cmakeVersion into gradle.properties, but
 * AGP does not read that property. It uses android.externalNativeBuild.cmake.version
 * or else defaults to CMake 3.22.1. This plugin sets that Gradle DSL field to
 * 3.31.6 (Ninja 1.12+) and keeps CMAKE_OBJECT_PATH_MAX=1024.
 */
const { withAppBuildGradle } = require("expo/config-plugins");

const CMAKE_VERSION = "3.31.6";
const FLAG = "-DCMAKE_OBJECT_PATH_MAX=1024";
const VERSION_MARKER = "expo-cmake-version";

function ensureCmakeVersion(src, isKts) {
  if (src.includes(VERSION_MARKER)) return src;
  const already =
    src.includes(`version "${CMAKE_VERSION}"`) || src.includes(`version = "${CMAKE_VERSION}"`);
  if (already) return src;
  if (!/android\s*\{/.test(src)) return src;
  const block = isKts
    ? `
    // ${VERSION_MARKER}
    externalNativeBuild {
        cmake {
            version = "${CMAKE_VERSION}"
        }
    }
`
    : `
    // ${VERSION_MARKER}
    externalNativeBuild {
        cmake {
            version "${CMAKE_VERSION}"
        }
    }
`;
  return src.replace(/android\s*\{/, (m) => `${m}${block}`);
}

function ensureObjectPathMax(src, isKts) {
  if (src.includes("CMAKE_OBJECT_PATH_MAX")) return src;
  if (!/defaultConfig\s*\{/.test(src)) return src;
  const block = isKts
    ? `
        externalNativeBuild {
            cmake {
                arguments += listOf("${FLAG}")
            }
        }
`
    : `
        externalNativeBuild {
            cmake {
                arguments "${FLAG}"
            }
        }
`;
  return src.replace(/defaultConfig\s*\{/, (m) => `${m}${block}`);
}

function withCmakeObjectPathMax(config) {
  return withAppBuildGradle(config, (mod) => {
    const isKts = mod.modResults.language === "kts";
    let src = mod.modResults.contents;
    src = ensureCmakeVersion(src, isKts);
    src = ensureObjectPathMax(src, isKts);
    mod.modResults.contents = src;
    return mod;
  });
}

module.exports = withCmakeObjectPathMax;
