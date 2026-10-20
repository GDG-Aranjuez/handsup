package es.gdgaranjuez.handsup.domain

/** Votes desc then oldest first, or oldest first. */
fun sortQuestions(list: List<Question>, mode: SortMode): List<Question> = when (mode) {
    SortMode.VOTES -> list.sortedWith(compareByDescending<Question> { it.votes }.thenBy { it.createdAtMs })
    SortMode.TIME -> list.sortedBy { it.createdAtMs }
}

/** A talk's questions grouped into the lanes an organizer works with. */
data class QueueView(
    val onStage: List<Question>,
    val open: List<Question>,
    val pending: List<Question>,
    val followup: List<Question>,
    val answered: List<Question>,
    val hidden: List<Question>,
)

fun buildQueue(list: List<Question>, mode: SortMode): QueueView {
    fun pick(s: QuestionStatus) = list.filter { it.status == s }
    return QueueView(
        onStage = sortQuestions(pick(QuestionStatus.ONSTAGE), SortMode.TIME),
        open = sortQuestions(pick(QuestionStatus.OPEN), mode),
        pending = sortQuestions(pick(QuestionStatus.PENDING), SortMode.TIME),
        followup = sortQuestions(pick(QuestionStatus.FOLLOWUP), mode),
        answered = sortQuestions(pick(QuestionStatus.ANSWERED), SortMode.TIME),
        hidden = sortQuestions(pick(QuestionStatus.HIDDEN), SortMode.TIME),
    )
}
