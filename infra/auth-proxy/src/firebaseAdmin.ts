import admin from "firebase-admin";
import { getEnv, getServiceAccountJson } from "./env.js";

/**
 * Initialize Firebase Admin SDK as a process-singleton. Subsequent calls
 * are no-ops (idempotent — Admin SDK throws if init'd twice without check).
 */
let initialized = false;

export function initFirebaseAdmin(): admin.app.App {
    if (initialized) {
        return admin.app();
    }
    const env = getEnv();
    const serviceAccount = getServiceAccountJson();
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount as admin.ServiceAccount),
        projectId: env.FIREBASE_PROJECT_ID,
    });
    initialized = true;
    return admin.app();
}

/** Sugar accessor — initializes on first use, returns the singleton. */
export function firebaseApp(): admin.app.App {
    return initFirebaseAdmin();
}
