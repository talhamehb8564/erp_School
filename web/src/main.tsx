import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { SessionProvider } from "./lib/session";
import { BrandingProvider } from "./lib/branding";
import { ToastProvider } from "./lib/toast";
import { applyTheme, readTheme } from "./lib/theme";
import "./styles.css";

applyTheme(readTheme());

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <BrandingProvider>
          <SessionProvider>
            <App />
          </SessionProvider>
        </BrandingProvider>
      </ToastProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
