const http = require("node:http");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const PORT = Number(process.env.PORT || 8080);
const DATA_FILE = path.join(__dirname, "motomove-live-db.json");

const sseClients = new Set();

function nowMs() {
  return Date.now();
}

function hashDemo(password) {
  return crypto.createHash("sha256").update(password).digest("hex");
}

function createSeedState() {
  const now = nowMs();
  const todayEpoch = Math.floor(now / 86400000);
  const demoPass = hashDemo("demo1234");
  return {
    users: [
      { userId: "u_tourist_1", name: "Aarav Sharma", contact: "tourist@motomove.in", passHash: demoPass, role: "TOURIST" },
      { userId: "u_tourist_2", name: "Neha Verma", contact: "neha@motomove.in", passHash: demoPass, role: "TOURIST" },
      { userId: "u_owner_1", name: "Parvati Wheels Rental", contact: "owner@motomove.in", passHash: demoPass, role: "VEHICLE_OWNER" },
      { userId: "u_owner_2", name: "Tirthan 4x4 Fleet", contact: "tirthan@motomove.in", passHash: demoPass, role: "VEHICLE_OWNER" },
      { userId: "u_owner_3", name: "Kinnaur Mountain Rides", contact: "kinnaur@motomove.in", passHash: demoPass, role: "VEHICLE_OWNER" },
      { userId: "u_driver_1", name: "Vikram Negi", contact: "driver@motomove.in", passHash: demoPass, role: "LOCAL_DRIVER" },
      { userId: "u_driver_2", name: "Sonam Dorje", contact: "spiti.driver@motomove.in", passHash: demoPass, role: "LOCAL_DRIVER" },
      { userId: "u_driver_3", name: "Pankaj Thakur", contact: "pankaj.driver@motomove.in", passHash: demoPass, role: "LOCAL_DRIVER" },
      { userId: "u_host_1", name: "Meera Chauhan", contact: "host@motomove.in", passHash: demoPass, role: "ACCOMMODATION_PROVIDER" },
      { userId: "u_host_2", name: "Tashi Bodh", contact: "tashi.stays@motomove.in", passHash: demoPass, role: "ACCOMMODATION_PROVIDER" },
      { userId: "u_admin_1", name: "SIH Pilot Admin", contact: "admin@motomove.in", passHash: demoPass, role: "ADMINISTRATOR" }
    ],
    providers: [
      { providerId: "p_owner_1", userId: "u_owner_1", displayName: "Parvati Wheels Rental", providerType: "VEHICLE_OWNER", verificationStatus: "VERIFIED", homeCluster: "Kasol–Manikaran–Tosh", lastUpdatedAt: now },
      { providerId: "p_owner_2", userId: "u_owner_2", displayName: "Tirthan 4x4 Fleet", providerType: "VEHICLE_OWNER", verificationStatus: "VERIFIED", homeCluster: "Jibhi–Tirthan Valley", lastUpdatedAt: now },
      { providerId: "p_owner_3", userId: "u_owner_3", displayName: "Kinnaur Mountain Rides", providerType: "VEHICLE_OWNER", verificationStatus: "VERIFIED", homeCluster: "Sangla–Chitkul–Kalpa", lastUpdatedAt: now },
      { providerId: "p_driver_1", userId: "u_driver_1", displayName: "Vikram Negi", providerType: "LOCAL_DRIVER", verificationStatus: "VERIFIED", homeCluster: "Kasol–Manikaran–Tosh", lastUpdatedAt: now },
      { providerId: "p_driver_2", userId: "u_driver_2", displayName: "Sonam Dorje", providerType: "LOCAL_DRIVER", verificationStatus: "PENDING", homeCluster: "Kaza–Kibber–Langza–Hikkim", lastUpdatedAt: now },
      { providerId: "p_driver_3", userId: "u_driver_3", displayName: "Pankaj Thakur", providerType: "LOCAL_DRIVER", verificationStatus: "VERIFIED", homeCluster: "Jibhi–Tirthan Valley", lastUpdatedAt: now },
      { providerId: "p_host_1", userId: "u_host_1", displayName: "Meera Chauhan", providerType: "ACCOMMODATION_PROVIDER", verificationStatus: "VERIFIED", homeCluster: "Jibhi–Tirthan Valley", lastUpdatedAt: now },
      { providerId: "p_host_2", userId: "u_host_2", displayName: "Tashi Bodh", providerType: "ACCOMMODATION_PROVIDER", verificationStatus: "VERIFIED", homeCluster: "Kaza–Kibber–Langza–Hikkim", lastUpdatedAt: now }
    ],
    vehicles: [
      { vehicleId: "veh_1", providerId: "p_owner_1", ownerName: "Parvati Wheels Rental", title: "Royal Enfield Classic 350 Stealth", vehicleType: "Cruiser Bike", capacity: 2, transmissionFuel: "5-Speed • Petrol", serviceArea: "Kasol–Manikaran–Tosh, Jibhi–Tirthan Valley", pickupHubs: ["KUU", "DHM", "JDNX"], rentalMode: "SELF_DRIVE", rateInr: 1500, rateBasis: "per day", cancellationTerms: "Valid DL required at handover.", approvalStatus: "APPROVED", providerVerification: "VERIFIED", isAvailableNow: true, liveStatusNote: "Ready at Bhuntar Airport Gate 1", lastUpdatedAt: now },
      { vehicleId: "veh_2", providerId: "p_owner_2", ownerName: "Tirthan 4x4 Fleet", title: "Mahindra Scorpio-N 4x4 (Hill Permit)", vehicleType: "4x4 SUV", capacity: 6, transmissionFuel: "Manual • Diesel 4WD", serviceArea: "Jibhi–Tirthan Valley, Kasol–Manikaran–Tosh, Kaza–Kibber–Langza–Hikkim", pickupHubs: ["KUU", "IXC", "KLK"], rentalMode: "WITH_DRIVER", rateInr: 3800, rateBasis: "per day", cancellationTerms: "Free cancellation 12h prior.", approvalStatus: "APPROVED", providerVerification: "VERIFIED", isAvailableNow: true, liveStatusNote: "Parked near Bhuntar / Aut Tunnel", lastUpdatedAt: now },
      { vehicleId: "veh_3", providerId: "p_owner_2", ownerName: "Tirthan 4x4 Fleet", title: "Maruti Jimny 4x4 (Snow & Narrow Roads)", vehicleType: "Compact 4x4 SUV", capacity: 4, transmissionFuel: "Manual • Petrol 4x4", serviceArea: "Jibhi–Tirthan Valley, Bir–Billing & Barot Valley, Shimla–Narkanda", pickupHubs: ["KUU", "KLK", "SML", "SLV", "JDNX"], rentalMode: "BOTH", rateInr: 3200, rateBasis: "per day", cancellationTerms: "Free reschedule for train/flight delays.", approvalStatus: "APPROVED", providerVerification: "VERIFIED", isAvailableNow: true, liveStatusNote: "Available for Kalka & Bhuntar pickups", lastUpdatedAt: now },
      { vehicleId: "veh_4", providerId: "p_owner_1", ownerName: "Parvati Wheels Rental", title: "Royal Enfield Himalayan 450", vehicleType: "Adventure Bike", capacity: 2, transmissionFuel: "6-Speed • Petrol", serviceArea: "Kasol–Manikaran–Tosh, Sangla–Chitkul–Kalpa", pickupHubs: ["KUU", "KLK", "IXC"], rentalMode: "SELF_DRIVE", rateInr: 1600, rateBasis: "per day", cancellationTerms: "2 helmets included.", approvalStatus: "APPROVED", providerVerification: "VERIFIED", isAvailableNow: true, liveStatusNote: "Ready for pickup", lastUpdatedAt: now },
      { vehicleId: "veh_5", providerId: "p_owner_3", ownerName: "Kinnaur Mountain Rides", title: "Toyota Innova Crysta (Airport VIP Pickup)", vehicleType: "MPV / Family Cab", capacity: 7, transmissionFuel: "Diesel • Hill Cruise", serviceArea: "Shimla–Narkanda, Sangla–Chitkul–Kalpa, Jibhi–Tirthan Valley", pickupHubs: ["KLK", "SML", "SLV", "IXC"], rentalMode: "WITH_DRIVER", rateInr: 4200, rateBasis: "per day", cancellationTerms: "90m delay wait included.", approvalStatus: "APPROVED", providerVerification: "VERIFIED", isAvailableNow: true, liveStatusNote: "At Kalka Railway Station Bay", lastUpdatedAt: now },
      { vehicleId: "veh_6", providerId: "p_owner_3", ownerName: "Kinnaur Mountain Rides", title: "Force Gurkha 4x4 (Spiti & Chitkul Edition)", vehicleType: "Extreme 4x4 SUV", capacity: 5, transmissionFuel: "Diesel 4x4 • Snorkel", serviceArea: "Kaza–Kibber–Langza–Hikkim, Sangla–Chitkul–Kalpa", pickupHubs: ["KLK", "SML", "KUU", "IXC"], rentalMode: "WITH_DRIVER", rateInr: 4600, rateBasis: "per day", cancellationTerms: "Snow chains & oxygen included.", approvalStatus: "APPROVED", providerVerification: "VERIFIED", isAvailableNow: true, liveStatusNote: "Snow-ready 4x4", lastUpdatedAt: now }
    ],
    drivers: [
      { driverId: "drv_1", providerId: "p_driver_1", name: "Vikram Negi", languages: "Hindi, English, Pahari, Kinnauri", experienceYears: 11, serviceArea: "Kasol–Manikaran–Tosh, Sangla–Chitkul–Kalpa, Jibhi–Tirthan Valley", pickupHubs: ["KUU", "KLK", "SML", "SLV", "IXC"], ratePerDayInr: 1400, availabilitySummary: "Online • Ready at Bhuntar & Kalka", cancellationTerms: "Free cancellation 6h prior.", verificationStatus: "VERIFIED", approvalStatus: "APPROVED", isAvailableNow: true, lastUpdatedAt: now },
      { driverId: "drv_2", providerId: "p_driver_2", name: "Sonam Dorje", languages: "Hindi, Spiti Bhoti, English", experienceYears: 9, serviceArea: "Kaza–Kibber–Langza–Hikkim, Sangla–Chitkul–Kalpa", pickupHubs: ["KUU", "KLK", "IXC"], ratePerDayInr: 1800, availabilitySummary: "Pending admin verification", cancellationTerms: "Weather flexible.", verificationStatus: "PENDING", approvalStatus: "PENDING_REVIEW", isAvailableNow: false, lastUpdatedAt: now },
      { driverId: "drv_3", providerId: "p_driver_3", name: "Pankaj Thakur", languages: "Hindi, English, Kulluvi", experienceYears: 8, serviceArea: "Jibhi–Tirthan Valley, Kasol–Manikaran–Tosh", pickupHubs: ["KUU", "JDNX", "IXC"], ratePerDayInr: 1300, availabilitySummary: "Online • Aut Tunnel & Jalori Pass expert", cancellationTerms: "Free reschedule 6h prior.", verificationStatus: "VERIFIED", approvalStatus: "APPROVED", isAvailableNow: true, lastUpdatedAt: now }
    ],
    properties: [
      { propertyId: "prop_1", providerId: "p_host_1", hostName: "Meera Chauhan", name: "Tirthan Cedar Creek Homestay", propertyType: "Homestay", locationCluster: "Jibhi–Tirthan Valley", nearestHubCode: "KUU", amenities: "Bukhari wood heater, Home-cooked meals, Solar backup", policies: "Check-in 12:00 / Check-out 10:00", registrationNumber: "HP-KUL-HS-2025-418", approvalStatus: "APPROVED", providerVerification: "VERIFIED", lastUpdatedAt: now },
      { propertyId: "prop_2", providerId: "p_host_1", hostName: "Meera Chauhan", name: "Tosh Orchard Wooden Stay", propertyType: "Guesthouse", locationCluster: "Kasol–Manikaran–Tosh", nearestHubCode: "KUU", amenities: "Mountain balcony, Hot water, Trail guide", policies: "Check-in 13:00 / Check-out 11:00", registrationNumber: "HP-KUL-GH-2025-902", approvalStatus: "APPROVED", providerVerification: "VERIFIED", lastUpdatedAt: now },
      { propertyId: "prop_3", providerId: "p_host_2", hostName: "Tashi Bodh", name: "Spiti Starlight Mud-House & Hotel", propertyType: "Boutique Hotel", locationCluster: "Kaza–Kibber–Langza–Hikkim", nearestHubCode: "KUU", amenities: "Bukhari heating, Oxygen backup, Stargazing deck", policies: "Check-in 12:00 / Check-out 10:00", registrationNumber: "HP-LS-HT-2025-114", approvalStatus: "APPROVED", providerVerification: "VERIFIED", lastUpdatedAt: now }
    ],
    roomTypes: [
      { roomTypeId: "room_1", propertyId: "prop_1", name: "Deodar Valley Attic Room", occupancy: 3, ratePerNightInr: 2200, inventoryCount: 2 },
      { roomTypeId: "room_2", propertyId: "prop_1", name: "Family Riverfront Cottage", occupancy: 5, ratePerNightInr: 3900, inventoryCount: 1 },
      { roomTypeId: "room_3", propertyId: "prop_2", name: "Parvati View Timber Room", occupancy: 2, ratePerNightInr: 1800, inventoryCount: 3 },
      { roomTypeId: "room_5", propertyId: "prop_3", name: "Heated Monastery View Room", occupancy: 2, ratePerNightInr: 2800, inventoryCount: 3 }
    ],
    diningSpots: [
      { spotId: "food_1", providerId: "p_host_1", name: "The Himalayan Trout House & Cafe", locationCluster: "Jibhi–Tirthan Valley", specialty: "Fresh Trout", dishes: "Fresh Rainbow Trout, Herb Butter, Smoked Trout", ambience: "Riverside Deck • Top Rated", avgCostInr: 750, isFssaiVerified: true, rating: 4.9, isOpenNow: true, liveStatusNote: "Fresh trout & 4 riverside tables open" },
      { spotId: "food_2", providerId: "p_host_1", name: "Mama's Mountain Siddu Kitchen", locationCluster: "Kasol–Manikaran–Tosh", specialty: "Pahadi Dham & Siddu", dishes: "Steamed Walnut Siddu, Chana Madra, Ghee", ambience: "Cozy Stone Hearth • Local Favorite", avgCostInr: 250, isFssaiVerified: true, rating: 4.8, isOpenNow: true, liveStatusNote: "Hot Siddu batch ready" },
      { spotId: "food_3", providerId: "p_host_2", name: "Sol Monastery Bakery & Tea", locationCluster: "Kaza–Kibber–Langza–Hikkim", specialty: "Woodfired Cafe", dishes: "Yak Cheese Pizza, Sea Buckthorn Tea, Apple Tart", ambience: "Warm Bukhari Hall • 3,800m", avgCostInr: 480, isFssaiVerified: true, rating: 4.7, isOpenNow: true, liveStatusNote: "Open • Woodfired oven warm" }
    ],
    bookings: [
      { bookingId: "bk_seed_2", touristId: "u_tourist_1", touristName: "Aarav Sharma", providerId: "p_owner_2", listingId: "veh_3", listingTitle: "Maruti Jimny 4x4 (Snow & Narrow Roads)", bookingType: "HUB_PICKUP", pickupHubCode: "KLK", destinationCluster: "Shimla–Narkanda", trainOrFlightNo: "12011 Kalka Shatabdi", pickupNotes: "Pickup from Kalka Platform 1 Exit Bay", startEpochDay: todayEpoch + 2, endEpochDay: todayEpoch + 3, slot: "AFTERNOON_12_18", guestOrSeatCount: 3, status: "PENDING", quotedPriceInr: 6400, cancellationTerms: "Free reschedule 3h.", createdAt: now - 1800000, respondedAt: null }
    ],
    notifications: [
      { notificationId: "not_live_1", userId: "u_tourist_1", category: "TRIPS", eventType: "LIVE_SYNC", message: "Connected to MotoMove Live! Real-time driver, hotel & dining updates active.", isRead: false, createdAt: now }
    ]
  };
}

function loadState() {
  if (fs.existsSync(DATA_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
    } catch (_) {}
  }
  const initial = createSeedState();
  fs.writeFileSync(DATA_FILE, JSON.stringify(initial, null, 2));
  return initial;
}

let db = loadState();

function saveState() {
  fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2));
}

function broadcast(eventType, summary, targetUserId = null) {
  const payload = JSON.stringify({
    type: eventType,
    summary,
    targetUserId,
    timestamp: nowMs()
  });
  for (const res of sseClients) {
    try {
      res.write(`event: sync\ndata: ${payload}\n\n`);
    } catch (_) {
      sseClients.delete(res);
    }
  }
}

function sendJson(res, code, data) {
  const body = JSON.stringify(data);
  res.writeHead(code, {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, x-user-id"
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 1_000_000) reject(new Error("Payload too large"));
    });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (e) {
        reject(e);
      }
    });
  });
}

function getActor(req) {
  const userId = req.headers["x-user-id"];
  if (!userId) return null;
  return db.users.find(u => u.userId === userId) || null;
}

function getProviderForUser(userId) {
  return db.providers.find(p => p.userId === userId) || null;
}

function pushNotification(userId, category, eventType, message) {
  db.notifications.unshift({
    notificationId: "not_" + crypto.randomUUID().slice(0, 8),
    userId,
    category,
    eventType,
    message,
    isRead: false,
    createdAt: nowMs()
  });
}

function notifyAllTourists(category, eventType, message) {
  for (const u of db.users.filter(x => x.role === "TOURIST")) {
    pushNotification(u.userId, category, eventType, message);
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const pathname = url.pathname;

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, x-user-id"
    });
    return res.end();
  }

  if (req.method === "GET" && pathname === "/api/health") {
    return sendJson(res, 200, { ok: true, clients: sseClients.size, time: nowMs() });
  }

  if (req.method === "GET" && pathname === "/api/events") {
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
      "Access-Control-Allow-Origin": "*"
    });
    res.write(`event: connected\ndata: ${JSON.stringify({ ok: true, time: nowMs() })}\n\n`);
    sseClients.add(res);
    req.on("close", () => sseClients.delete(res));
    return;
  }

  if (req.method === "GET" && pathname === "/api/state") {
    const userId = url.searchParams.get("userId");
    const user = db.users.find(u => u.userId === userId) || null;
    const provider = user ? getProviderForUser(user.userId) : null;
    const isPrivileged = user && (user.role === "ADMINISTRATOR" || !!provider);

    const vehicles = isPrivileged
      ? db.vehicles
      : db.vehicles.filter(v => v.approvalStatus === "APPROVED" && v.providerVerification === "VERIFIED");
    const drivers = isPrivileged
      ? db.drivers
      : db.drivers.filter(d => d.approvalStatus === "APPROVED" && d.verificationStatus === "VERIFIED");
    const properties = isPrivileged
      ? db.properties
      : db.properties.filter(p => p.approvalStatus === "APPROVED" && p.providerVerification === "VERIFIED");

    const bookings = !user
      ? []
      : user.role === "ADMINISTRATOR"
        ? db.bookings
        : user.role === "TOURIST"
          ? db.bookings.filter(b => b.touristId === user.userId)
          : db.bookings.filter(b => b.providerId === (provider && provider.providerId));

    const notifications = user ? db.notifications.filter(n => n.userId === user.userId) : [];

    return sendJson(res, 200, {
      vehicles,
      drivers,
      properties,
      roomTypes: db.roomTypes,
      diningSpots: db.diningSpots,
      bookings,
      notifications,
      serverTime: nowMs()
    });
  }

  try {
    const body = req.method !== "GET" ? await readBody(req) : {};

    // Vehicle live availability & price update (strictly owner-only)
    if (req.method === "PATCH" && pathname.startsWith("/api/vehicles/") && pathname.endsWith("/live")) {
      const actor = getActor(req);
      if (!actor) return sendJson(res, 401, { error: "Sign in required." });
      const vehicleId = pathname.split("/")[3];
      const vehicle = db.vehicles.find(v => v.vehicleId === vehicleId);
      if (!vehicle) return sendJson(res, 404, { error: "Vehicle not found." });
      const prov = getProviderForUser(actor.userId);
      if (!prov || prov.providerId !== vehicle.providerId) {
        return sendJson(res, 403, { error: "Access Denied: You cannot edit another owner's vehicle." });
      }
      if (typeof body.isAvailableNow === "boolean") vehicle.isAvailableNow = body.isAvailableNow;
      if (typeof body.rateInr === "number" && body.rateInr > 0) vehicle.rateInr = body.rateInr;
      if (typeof body.liveStatusNote === "string") vehicle.liveStatusNote = body.liveStatusNote.trim();
      vehicle.lastUpdatedAt = nowMs();

      const msg = `${vehicle.ownerName}: ${vehicle.title} is now ${vehicle.isAvailableNow ? "AVAILABLE NOW" : "ON TRIP"} (₹${vehicle.rateInr}/day) — ${vehicle.liveStatusNote}`;
      notifyAllTourists("TRIPS", "LIVE_VEHICLE_UPDATE", msg);
      saveState();
      broadcast("VEHICLE_LIVE", msg);
      return sendJson(res, 200, vehicle);
    }

    // Driver live availability toggle (strictly owner-only)
    if (req.method === "PATCH" && pathname.startsWith("/api/drivers/") && pathname.endsWith("/live")) {
      const actor = getActor(req);
      if (!actor) return sendJson(res, 401, { error: "Sign in required." });
      const driverId = pathname.split("/")[3];
      const driver = db.drivers.find(d => d.driverId === driverId);
      if (!driver) return sendJson(res, 404, { error: "Driver not found." });
      const prov = getProviderForUser(actor.userId);
      if (!prov || prov.providerId !== driver.providerId) {
        return sendJson(res, 403, { error: "Access Denied: You cannot edit another driver's status." });
      }
      if (typeof body.isAvailableNow === "boolean") driver.isAvailableNow = body.isAvailableNow;
      if (typeof body.ratePerDayInr === "number" && body.ratePerDayInr > 0) driver.ratePerDayInr = body.ratePerDayInr;
      if (typeof body.availabilitySummary === "string") driver.availabilitySummary = body.availabilitySummary.trim();
      driver.lastUpdatedAt = nowMs();

      const msg = `Driver ${driver.name} is now ${driver.isAvailableNow ? "ONLINE & READY FOR PICKUP" : "BUSY / ON TRIP"} — ${driver.availabilitySummary}`;
      notifyAllTourists("TRIPS", "LIVE_DRIVER_UPDATE", msg);
      saveState();
      broadcast("DRIVER_LIVE", msg);
      return sendJson(res, 200, driver);
    }

    // Hotel/Homestay room live inventory update (strictly host-only)
    if (req.method === "PATCH" && pathname.startsWith("/api/rooms/") && pathname.endsWith("/live")) {
      const actor = getActor(req);
      if (!actor) return sendJson(res, 401, { error: "Sign in required." });
      const roomId = pathname.split("/")[3];
      const room = db.roomTypes.find(r => r.roomTypeId === roomId);
      if (!room) return sendJson(res, 404, { error: "Room type not found." });
      const prop = db.properties.find(p => p.propertyId === room.propertyId);
      const prov = getProviderForUser(actor.userId);
      if (!prop || !prov || prov.providerId !== prop.providerId) {
        return sendJson(res, 403, { error: "Access Denied: You cannot edit another host's room inventory." });
      }
      if (typeof body.inventoryCount === "number" && body.inventoryCount >= 0) room.inventoryCount = body.inventoryCount;
      if (typeof body.ratePerNightInr === "number" && body.ratePerNightInr > 0) room.ratePerNightInr = body.ratePerNightInr;
      prop.lastUpdatedAt = nowMs();

      const msg = `${prop.name}: ${room.name} now has ${room.inventoryCount} room(s) left at ₹${room.ratePerNightInr}/night!`;
      notifyAllTourists("DEALS", "LIVE_ROOM_UPDATE", msg);
      saveState();
      broadcast("STAY_LIVE", msg);
      return sendJson(res, 200, room);
    }

    // Food / Dining spot live open/tables update (strictly host-only)
    if (req.method === "PATCH" && pathname.startsWith("/api/dining/") && pathname.endsWith("/live")) {
      const actor = getActor(req);
      if (!actor) return sendJson(res, 401, { error: "Sign in required." });
      const spotId = pathname.split("/")[3];
      const spot = db.diningSpots.find(s => s.spotId === spotId);
      if (!spot) return sendJson(res, 404, { error: "Dining spot not found." });
      const prov = getProviderForUser(actor.userId);
      if (!prov || prov.providerId !== spot.providerId) {
        return sendJson(res, 403, { error: "Access Denied: You cannot edit another host's dining spot." });
      }
      if (typeof body.isOpenNow === "boolean") spot.isOpenNow = body.isOpenNow;
      if (typeof body.liveStatusNote === "string") spot.liveStatusNote = body.liveStatusNote.trim();
      if (typeof body.avgCostInr === "number" && body.avgCostInr > 0) spot.avgCostInr = body.avgCostInr;

      const msg = `${spot.name} (${spot.locationCluster}): ${spot.isOpenNow ? "TABLES OPEN" : "CURRENTLY FULL"} — ${spot.liveStatusNote}`;
      notifyAllTourists("DEALS", "LIVE_FOOD_UPDATE", msg);
      saveState();
      broadcast("FOOD_LIVE", msg);
      return sendJson(res, 200, spot);
    }

    // Create Booking (Tourist only -> instant live notification to Provider!)
    if (req.method === "POST" && pathname === "/api/bookings") {
      const actor = getActor(req);
      if (!actor || actor.role !== "TOURIST") {
        return sendJson(res, 403, { error: "Only Tourist accounts can submit bookings." });
      }
      const booking = {
        bookingId: "bk_" + crypto.randomUUID().slice(0, 8),
        touristId: actor.userId,
        touristName: actor.name,
        providerId: body.providerId,
        listingId: body.listingId,
        listingTitle: body.listingTitle,
        bookingType: body.bookingType,
        pickupHubCode: body.pickupHubCode || null,
        destinationCluster: body.destinationCluster,
        trainOrFlightNo: body.trainOrFlightNo || null,
        pickupNotes: body.pickupNotes || "",
        startEpochDay: Number(body.startEpochDay),
        endEpochDay: Number(body.endEpochDay),
        slot: body.slot || "MORNING_06_12",
        guestOrSeatCount: Number(body.guestOrSeatCount || 1),
        status: body.bookingType === "DINING" ? "ACCEPTED" : "PENDING",
        quotedPriceInr: Number(body.quotedPriceInr || 0),
        cancellationTerms: body.cancellationTerms || "Standard terms",
        createdAt: nowMs(),
        respondedAt: body.bookingType === "DINING" ? nowMs() : null
      };
      db.bookings.unshift(booking);
      const prov = db.providers.find(p => p.providerId === booking.providerId);
      if (prov) {
        pushNotification(prov.userId, "TRIPS", "NEW_BOOKING_LIVE", `LIVE REQUEST: ${actor.name} requested ${booking.listingTitle} (${booking.pickupHubCode || booking.destinationCluster}).`);
      }
      saveState();
      broadcast("BOOKING_NEW", `New live booking for ${booking.listingTitle}`, prov && prov.userId);
      return sendJson(res, 201, booking);
    }

    // Provider Accept / Reject Booking (strictly owning provider!)
    if (req.method === "POST" && pathname.startsWith("/api/bookings/") && pathname.endsWith("/respond")) {
      const actor = getActor(req);
      if (!actor) return sendJson(res, 401, { error: "Sign in required." });
      const bookingId = pathname.split("/")[3];
      const booking = db.bookings.find(b => b.bookingId === bookingId);
      if (!booking) return sendJson(res, 404, { error: "Booking not found." });
      const prov = getProviderForUser(actor.userId);
      if (!prov || prov.providerId !== booking.providerId) {
        return sendJson(res, 403, { error: "Access Denied: Only the owning provider can respond." });
      }
      booking.status = body.accept ? "ACCEPTED" : "REJECTED";
      booking.respondedAt = nowMs();
      pushNotification(booking.touristId, "TRIPS", `BOOKING_${booking.status}`, `LIVE UPDATE: Your request for ${booking.listingTitle} was ${booking.status}!`);
      saveState();
      broadcast("BOOKING_RESPONDED", `${booking.listingTitle} was ${booking.status}`, booking.touristId);
      return sendJson(res, 200, booking);
    }

    return sendJson(res, 404, { error: "Unknown endpoint" });
  } catch (err) {
    return sendJson(res, 400, { error: err.message || "Bad request" });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`MotoMove Live Sync Server listening on http://0.0.0.0:${PORT}`);
  console.log(`SSE stream: GET /api/events | State: GET /api/state`);
});
