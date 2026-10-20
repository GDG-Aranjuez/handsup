export type Delivery = 'speak' | 'read';
export type QuestionStatus =
  | 'pending'
  | 'open'
  | 'onstage'
  | 'answered'
  | 'followup'
  | 'hidden'
  | 'withdrawn';
export type TalkState = 'scheduled' | 'live' | 'ended';
export type SortMode = 'votes' | 'time';

export interface Branding {
  primary: string;
  accent: string;
  logo?: string;
  tagline?: string;
}
export interface EventSettings {
  voting: boolean;
  defaultSort: SortMode;
  preApproval: boolean;
  graceMin: number;
}
export interface EventDoc {
  id: string;
  name: string;
  branding: Branding;
  settings: EventSettings;
}
export interface Room {
  id: string;
  name: string;
  liveTalkId: string | null;
  order: number;
}
export interface Talk {
  id: string; // the Talk code
  title: string;
  speakers: string;
  roomId: string;
  start: Date;
  end: Date;
  state: TalkState;
  questionsClosed: boolean;
  endedAt?: Date;
}
export interface Answer {
  text: string;
  at: Date;
}
export interface Question {
  id: string;
  text: string;
  delivery: Delivery;
  authorUid: string;
  pseudonym: string;
  status: QuestionStatus;
  votes: number;
  createdAt: Date;
  answer?: Answer;
}
export interface Attendee {
  uid: string;
  pseudonym: string;
}

export const VISIBLE_STATUSES: QuestionStatus[] = ['open', 'onstage', 'answered', 'followup'];
export const MAX_QUESTION_LENGTH = 280;
export const DEFAULT_BRANDING: Branding = { primary: '#d91e5b', accent: '#1e6ad9' };
export const DEFAULT_SETTINGS: EventSettings = {
  voting: true,
  defaultSort: 'votes',
  preApproval: false,
  graceMin: 10,
};
