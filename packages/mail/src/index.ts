export { ME, SEED_MESSAGES, SEED_TASKS, SEED_CONTACTS, EVENTS, BOXES } from "./model.ts";
export type {
  Person,
  FolderId,
  BoxId,
  MailMessage,
  ConductorTask,
  Contact,
  CalEvent,
  ViewId,
} from "./model.ts";
export {
  isCumulus,
  isE2E,
  inBox,
  formatWhen,
  formatShort,
  initials,
  listThreads,
  threadOf,
  unreadInBox,
  draftCount,
  rowTitle,
  quota,
  parsePeople,
  peopleToField,
  validEmail,
  transcript,
  quote,
  briefKey,
} from "./logic.ts";
export { Mailbox } from "./mailbox.ts";
export type { MailSnapshot, SendInput } from "./mailbox.ts";
