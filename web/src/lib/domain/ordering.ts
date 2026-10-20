import type { Question, SortMode } from './types';

/** Votes desc then oldest first, or oldest first. */
export function sortQuestions(list: Question[], mode: SortMode): Question[] {
  const byTime = (a: Question, b: Question) => a.createdAt.getTime() - b.createdAt.getTime();
  return [...list].sort(
    mode === 'votes' ? (a, b) => b.votes - a.votes || byTime(a, b) : byTime,
  );
}

export interface QueueView {
  onStage: Question[];
  open: Question[];
  pending: Question[];
  followup: Question[];
  answered: Question[];
  hidden: Question[];
}

/** Groups a talk's questions into the lanes the organizer works with. */
export function buildQueue(list: Question[], mode: SortMode): QueueView {
  const pick = (s: Question['status']) => list.filter((q) => q.status === s);
  return {
    onStage: sortQuestions(pick('onstage'), 'time'),
    open: sortQuestions(pick('open'), mode),
    pending: sortQuestions(pick('pending'), 'time'),
    followup: sortQuestions(pick('followup'), mode),
    answered: sortQuestions(pick('answered'), 'time'),
    hidden: sortQuestions(pick('hidden'), 'time'),
  };
}
