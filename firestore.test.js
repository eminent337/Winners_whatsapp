const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_teacher_1";
const BOB_UID = "bob_teacher_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validStudentPayload(uid, now) {
  return {
    userId: uid,
    fullName: "Kwame Mensah",
    studentCode: "GP-2026-001",
    gradeClass: "Grade 8 Emerald",
    parentName: "Mrs. Abena Mensah",
    parentWhatsApp: "+233241234567",
    attendanceDays: 62,
    totalTermDays: 65,
    createdAt: now,
    updatedAt: now,
  };
}

function validReportCardPayload(uid, now) {
  return {
    userId: uid,
    studentId: "student_1",
    studentName: "Kwame Mensah",
    studentCode: "GP-2026-001",
    gradeClass: "Grade 8 Emerald",
    parentName: "Mrs. Abena Mensah",
    parentWhatsApp: "+233241234567",
    schoolName: "Royal Oak International Academy",
    academicTerm: "Term 1 • 2026/2027",
    attendanceSummary: "62 / 65 Days (95%)",
    subjectsSummary: "Mathematics|92|A+|Excellent analytical skills\nEnglish|88|A|Articulate writer",
    overallScore: 90,
    overallGrade: "A+",
    classPosition: "1st of 32",
    teacherRemarks: "Exemplary student with outstanding leadership.",
    principalSignature: "Dr. E. Amedzro, Principal",
    whatsappStatus: "pending",
    dispatchCount: 0,
    lastSentAt: "",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or list students or report cards", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("students").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("report_cards").get()
  );
});

test("Authenticated owner can create and read their own student and report card", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("students")
      .doc("student_1")
      .set(validStudentPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("report_cards")
      .doc("report_1")
      .set(validReportCardPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("students").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("report_cards").get()
  );
});

test("Authenticated user cannot read or write another user's students or report cards", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("report_cards")
      .doc("report_1")
      .set(validReportCardPayload(ALICE_UID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("report_cards")
      .doc("report_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("report_cards")
      .doc("report_2")
      .set(validReportCardPayload(BOB_UID, now))
  );
});

test("Shadow update with ghost field or invalid whatsappStatus is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("report_cards")
    .doc("report_1");

  await assertSucceeds(docRef.set(validReportCardPayload(ALICE_UID, now)));

  // Ghost field injection
  await assertFails(docRef.update({ isVerified: true, updatedAt: new Date() }));

  // Invalid enum value
  await assertFails(
    docRef.update({
      whatsappStatus: "hacked_status",
      dispatchCount: 1,
      lastSentAt: "Today",
      updatedAt: new Date(),
    })
  );

  // Valid WhatsApp dispatch update succeeds
  await assertSucceeds(
    docRef.update({
      whatsappStatus: "sent",
      dispatchCount: 1,
      lastSentAt: "Nov 13, 2026 • 12:50 PM",
      updatedAt: new Date(),
    })
  );
});

test("Authenticated owner can create and read chat_messages while another user is blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const chatDocRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chat_messages")
    .doc("msg_1");

  await assertSucceeds(
    chatDocRef.set({
      userId: ALICE_UID,
      threadKey: "233244112233",
      studentId: "student_1",
      studentName: "Kwame Mensah",
      parentName: "Mrs. Abena Mensah",
      parentWhatsApp: "+233244112233",
      direction: "outgoing",
      messageText: "Please find Kwame's report card attached.",
      attachmentFileId: "file_1",
      attachmentFileName: "Kwame_Mensah_ReportCard.pdf",
      attachmentMimeType: "application/pdf",
      deliveryStatus: "sent",
      sentTimeLabel: "3:05 PM",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chat_messages").get()
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chat_messages")
      .doc("msg_1")
      .get()
  );
});
