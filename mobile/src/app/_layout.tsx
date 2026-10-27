import { useEffect } from "react";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import * as SplashScreen from "expo-splash-screen";
import { SessionProvider } from "../lib/session";
import { FeatureProvider } from "../lib/features";
import { AppearanceProvider, useAppearance } from "../lib/appearance";
import { NotificationWatcher } from "../lib/notifications";

void SplashScreen.preventAutoHideAsync();

function SplashHider() {
  useEffect(() => {
    void SplashScreen.hideAsync();
  }, []);
  return null;
}

function ThemedStack() {
  const { scheme, colors } = useAppearance();
  return (
    <>
      <StatusBar style={scheme === "dark" ? "light" : "dark"} />
      <SplashHider />
      <NotificationWatcher />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTintColor: colors.ink,
          headerShadowVisible: false,
          contentStyle: { backgroundColor: colors.bg },
          animation: "fade",
        }}
      >
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="login" options={{ headerShown: false }} />
        <Stack.Screen name="signin" options={{ headerShown: false }} />
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="students" options={{ title: "Students" }} />
        <Stack.Screen name="users" options={{ title: "Users" }} />
        <Stack.Screen name="campuses" options={{ title: "Campuses" }} />
        <Stack.Screen name="academics" options={{ title: "Academics" }} />
        <Stack.Screen name="timetable" options={{ title: "Timetable" }} />
        <Stack.Screen name="attendance" options={{ title: "Attendance" }} />
        <Stack.Screen name="teacher-attendance" options={{ title: "Staff attendance" }} />
        <Stack.Screen name="homework" options={{ title: "Homework" }} />
        <Stack.Screen name="exams" options={{ title: "Marks & results" }} />
        <Stack.Screen name="online-exams" options={{ title: "Online exams" }} />
        <Stack.Screen name="online-exam/[id]" options={{ title: "Exam" }} />
        <Stack.Screen name="fees" options={{ title: "Fees" }} />
        <Stack.Screen name="salaries" options={{ title: "Salaries" }} />
        <Stack.Screen name="announcements" options={{ title: "Announcements" }} />
        <Stack.Screen name="calendar" options={{ title: "Calendar" }} />
        <Stack.Screen name="reports" options={{ title: "Reports" }} />
        <Stack.Screen name="admissions" options={{ title: "Admissions" }} />
        <Stack.Screen name="settings" options={{ title: "Settings" }} />
        <Stack.Screen name="billing" options={{ title: "Subscription" }} />
        <Stack.Screen name="schools" options={{ title: "Schools" }} />
        <Stack.Screen name="school/[id]" options={{ title: "School" }} />
        <Stack.Screen name="subscriptions" options={{ title: "Subscriptions" }} />
        <Stack.Screen name="password" options={{ title: "Password" }} />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <SessionProvider>
          <FeatureProvider>
            <AppearanceProvider>
              <ThemedStack />
            </AppearanceProvider>
          </FeatureProvider>
        </SessionProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
