import { createRoot } from "react-dom/client";
import App from "./app/App.tsx";
import { installServiceWorkerUpdateReload, installStaleChunkReload } from "./app/lib/serviceWorkerUpdate.ts";
import "./styles/index.css";

installStaleChunkReload();
installServiceWorkerUpdateReload();
createRoot(document.getElementById("root")!).render(<App />);
