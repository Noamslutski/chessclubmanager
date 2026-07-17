/**
 * Cloud Functions for the Petach Tikva Chess Club app.
 *
 *  - ingestLichessPuzzles: scheduled job that pulls fresh puzzles from the
 *    Lichess API, reconstructs each FEN, and writes them to the shared
 *    Firestore `puzzles` pool (deduped by id). This is the "auto-upload
 *    Lichess puzzles" job — it runs server-side with no app open.
 *
 *  - notifyAdminOnRegistration: emails the club admin when a new registration
 *    document is created (via the Firebase "Trigger Email" extension, which
 *    delivers any document written to the `mail` collection).
 *
 * Deploy:  firebase deploy --only functions
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { Chess } = require("chess.js");

admin.initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 5 });
const db = admin.firestore();

const START_FEN =
  "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

// ---------------------------------------------------------------------------
// Scheduled Lichess puzzle ingestion
// ---------------------------------------------------------------------------

exports.ingestLichessPuzzles = onSchedule("every 24 hours", async () => {
  const raw = [];
  const daily = await getJson("https://lichess.org/api/puzzle/daily");
  if (daily) raw.push(daily);

  for (const difficulty of ["easiest", "easier", "normal", "harder", "hardest"]) {
    for (let i = 0; i < 3; i++) {
      const p = await getJson(
        "https://lichess.org/api/puzzle/next?difficulty=" + difficulty
      );
      if (p) raw.push(p);
    }
  }

  let batch = db.batch();
  let count = 0;
  for (const item of raw) {
    const rec = toPuzzleRecord(item);
    if (!rec) continue;
    batch.set(db.collection("puzzles").doc(rec.id), rec, { merge: true });
    count++;
  }
  if (count > 0) await batch.commit();
  logger.info(`Ingested ${count} Lichess puzzles into the shared pool.`);
});

function toPuzzleRecord(item) {
  try {
    const game = item.game || {};
    const puzzle = item.puzzle || {};
    const pgn = (game.pgn || "").trim();
    const solution = puzzle.solution || [];
    if (!pgn || solution.length === 0) return null;

    const fen = reconstructFen(pgn, puzzle.initialPly || 0, solution[0]);
    if (!fen) return null;

    const rating = puzzle.rating || 1500;
    return {
      id: puzzle.id,
      title: "Lichess • " + rating,
      fen,
      solutionUci: solution.join(" "),
      rating,
      level: levelForRating(rating),
      themes: puzzle.themes || [],
      source: "lichess",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };
  } catch (e) {
    logger.warn("Failed to parse a puzzle", e);
    return null;
  }
}

/**
 * Replays the game to the puzzle position. Plays `initialPly` plies, then — if
 * the first solution move is not for the side to move — advances one more ply,
 * matching the client's off-by-one-tolerant reconstruction.
 */
function reconstructFen(pgn, initialPly, firstSolution) {
  const source = new Chess();
  try {
    source.loadPgn(pgn);
  } catch (e) {
    return null;
  }
  const sans = source.history();

  const board = new Chess(START_FEN);
  let ply = 0;
  for (; ply < initialPly && ply < sans.length; ply++) {
    board.move(sans[ply]);
  }
  if (!ownsFrom(board, firstSolution) && ply < sans.length) {
    board.move(sans[ply]);
  }
  return board.fen();
}

function ownsFrom(board, uci) {
  if (!uci || uci.length < 2) return false;
  const piece = board.get(uci.slice(0, 2));
  return piece && piece.color === board.turn();
}

function levelForRating(rating) {
  if (rating < 1200) return 1;
  if (rating < 1500) return 2;
  if (rating < 1800) return 3;
  if (rating < 2100) return 4;
  return 5;
}

async function getJson(url) {
  try {
    const res = await fetch(url, {
      headers: {
        Accept: "application/json",
        "User-Agent": "PTChessClub/1.0",
      },
    });
    if (!res.ok) return null;
    return await res.json();
  } catch (e) {
    logger.warn("Fetch failed: " + url, e);
    return null;
  }
}

// ---------------------------------------------------------------------------
// Mirror the user profile (role, clubId) into Auth custom claims for the rules
// ---------------------------------------------------------------------------

exports.setUserClaims = onDocumentWritten("users/{uid}", async (event) => {
  const after = event.data && event.data.after && event.data.after.data();
  if (!after) return; // profile deleted
  const uid = event.params.uid;
  const claims = {
    role: after.role || "CHILD",
    clubId: after.clubId || 0,
  };
  try {
    await admin.auth().setCustomUserClaims(uid, claims);
    logger.info(`Set claims for ${uid}: ${JSON.stringify(claims)}`);
  } catch (e) {
    logger.warn("Failed to set claims for " + uid, e);
  }
});

// ---------------------------------------------------------------------------
// Email the club admin on a new registration
// ---------------------------------------------------------------------------

exports.notifyAdminOnRegistration = onDocumentCreated(
  "registrations/{regId}",
  async (event) => {
    const data = event.data && event.data.data();
    if (!data || !data.clubId) return;

    // Admin email: prefer the club document, else any admin user of that club.
    let adminEmail = null;
    const clubSnap = await db.collection("clubs").doc(String(data.clubId)).get();
    if (clubSnap.exists) adminEmail = clubSnap.get("adminEmail") || clubSnap.get("contactEmail");
    if (!adminEmail) {
      const admins = await db
        .collection("users")
        .where("clubId", "==", data.clubId)
        .where("role", "==", "ADMIN")
        .limit(1)
        .get();
      if (!admins.empty) adminEmail = admins.docs[0].get("email");
    }
    if (!adminEmail) {
      logger.info("No admin email found for club " + data.clubId);
      return;
    }

    // The Firebase "Trigger Email" extension sends any doc added to `mail`.
    await db.collection("mail").add({
      to: adminEmail,
      message: {
        subject: "בקשת הרשמה חדשה למועדון · New registration request",
        text:
          `${data.fullName || "A new member"} (${data.email || ""}) requested to ` +
          `join your club. Open the admin center to approve or reject.`,
      },
    });
    logger.info("Queued registration email to " + adminEmail);
  }
);
