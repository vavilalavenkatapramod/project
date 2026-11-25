# MotoMove Live Real-Time Sync Server

Zero-dependency Node.js server providing **Uber-like live updates** (via Server-Sent Events `/api/events` + REST `/api/state`) across multiple phones/emulators for Drivers, Vehicle Owners, Homestays/Hotels, Mountain Dining Spots, and Tourists.

## Start the Live Server
```powershell
cd C:\Users\vavil\.gemini\antigravity\scratch\MotoMoveServer
node server.js
```

## Connecting Devices
- **Android Emulator**: Uses `http://10.0.2.2:8080` automatically.
- **Two Real Android Phones (same Wi-Fi)**: Find your PC IPv4 (`ipconfig`, e.g., `192.168.1.25`), tap the **`● LIVE / OFFLINE`** badge in the top bar of MotoMove on both phones, and enter `http://192.168.1.25:8080`.
- **Over Mobile Data / Anywhere**: Host `server.js` on Render, Railway, or Fly.io (or expose port `8080` with ngrok/Cloudflare Tunnel) and paste the HTTPS URL into the app's Live Server dialog.
