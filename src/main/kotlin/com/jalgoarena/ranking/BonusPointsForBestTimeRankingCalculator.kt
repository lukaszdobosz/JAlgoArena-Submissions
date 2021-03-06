package com.jalgoarena.ranking

import com.jalgoarena.data.SubmissionsRepository
import com.jalgoarena.domain.*

class BonusPointsForBestTimeRankingCalculator(
        private val submissionsRepository: SubmissionsRepository,
        private val rankingCalculator: RankingCalculator
) : RankingCalculator {
    override fun userRankingDetails(
            user: User,
            problems: List<Problem>,
            users: List<User>
    ): List<SubmissionWithRankingDetails> {

        val userRankingDetails = rankingCalculator.userRankingDetails(
                user,
                problems,
                users
        )

        return userRankingDetails.map { submissionWithRankingDetails ->

            val bonusPoint = if (submissionWithRankingDetails.problemRankPlace == 1) 1.0 else 0.0
            val scoreWithBonus = submissionWithRankingDetails.score + bonusPoint

            val (problemRankPlace) = problemRanking(submissionWithRankingDetails.problemId, users, problems)
                    .mapIndexed { i, problemRankEntry -> Pair(i, problemRankEntry) }
                    .first { it.second.hacker == user.username }

            submissionWithRankingDetails.copy(
                    score = scoreWithBonus,
                    problemRankPlace = problemRankPlace + 1
            )
        }
    }

    override fun ranking(users: List<User>, submissions: List<Submission>, problems: List<Problem>): List<RankEntry> {

        val bonusPoints = calculateBonusPointsForFastestSolutions(submissions, users)

        return rankingCalculator.ranking(users, submissions, problems).map { rankEntry ->
            val id = users.first { it.username == rankEntry.hacker }.id

            RankEntry(
                    rankEntry.hacker,
                    rankEntry.score + bonusPoints[id] as Double,
                    rankEntry.solvedProblems,
                    rankEntry.region,
                    rankEntry.team,
                    rankEntry.numberOfSolutionsPerLanguage
            )
        }.sortedByDescending { it.score }
    }

    override fun problemRanking(problemId: String, users: List<User>, problems: List<Problem>): List<ProblemRankEntry> {
        val problemSubmissions = submissionsRepository.findByProblemId(problemId)

        val bonusPoints = calculateBonusPointsForFastestSolutions(problemSubmissions, users)

        return rankingCalculator.problemRanking(problemId, users, problems).map { problemRankEntry ->
            val user = users.filter { it.username == problemRankEntry.hacker }.first()

            ProblemRankEntry(
                    problemRankEntry.hacker,
                    problemRankEntry.score + bonusPoints[user.id] as Double,
                    problemRankEntry.elapsedTime,
                    problemRankEntry.language
            )
        }.sortedBy { it.elapsedTime }
    }

    private fun calculateBonusPointsForFastestSolutions(submissions: List<Submission>, users: List<User>): Map<String, Double> {

        val bonusPoints = mutableMapOf<String, Double>()
        users.forEach { bonusPoints[it.id] = 0.0 }

        val problems = submissions.map { it.problemId }.distinct()

        problems.forEach { problem ->
            val problemSubmissions = submissions.filter { it.problemId == problem }
            val fastestSubmission = problemSubmissions.minBy { it.elapsedTime }

            if (fastestSubmission != null && bonusPoints[fastestSubmission.userId] != null) {
                bonusPoints[fastestSubmission.userId] = bonusPoints[fastestSubmission.userId] as Double + 1.0
            }
        }

        return bonusPoints
    }
}
