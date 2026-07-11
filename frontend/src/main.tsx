import { createRoot } from "react-dom/client";
import App from "./app/App.tsx";
import { installServiceWorkerUpdateReload } from "./app/lib/serviceWorkerUpdate.ts";
import "./styles/index.css";

installServiceWorkerUpdateReload();
createRoot(document.getElementById("root")!).render(<App />);
