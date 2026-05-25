import { firebaseApp } from "../firebaseAdmin.js";

/**
 * Sends the high-priority FCM data message that wakes the callee's device to ring (Story 2.5,
 * background/lock-screen delivery). Data-only (no `notification` block) so the client's
 * `FirebaseMessagingService.onMessageReceived` always runs and posts its own full-screen
 * incoming-call notification — even in the background. The actual call state still resolves from
 * the `/pairs` `incomingCall` signal; this push is purely the "wake + ring" trigger.
 */
export interface IncomingCallPush {
    fcmToken: string;
    callerUid: string;
    callType: "audio" | "video";
}

export async function sendIncomingCallPush(input: IncomingCallPush): Promise<void> {
    await firebaseApp().messaging().send({
        token: input.fcmToken,
        data: {
            type: "incoming_call",
            callerUid: input.callerUid,
            callType: input.callType,
        },
        android: {
            // High priority + data-only → delivered promptly and wakes the app from the background.
            priority: "high",
        },
    });
}
