package io.pivotal.extremerstartup.question

import io.pivotal.extremerstartup.player.Log
import io.pivotal.extremerstartup.player.PlayerService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.DefaultUriBuilderFactory


@Component
class QuestionAsker(
        val restTemplate: RestTemplate,
        val playerService: PlayerService,
        val questionFactory: QuestionFactory
) {

    private val CORRECT_ANSWER_SCORE = 10
    private val MAX_INCORRECT_ANSWER_SCORE = -10
    private val STATUS_4XX_ERROR_SCORE = -40
    private val STATUS_5XX_ERROR_SCORE = -50

    @Scheduled(fixedRate = 5000)
    fun questionPlayers() {
        val allPlayers = playerService.listAll()
        allPlayers.sortedBy { -it.score }.forEachIndexed { index, it ->

            val question = questionFactory.nextQuestion(it)

            val uri = DefaultUriBuilderFactory()
                    .uriString(it.url.toString())
                    .queryParam("q", question.question)
                    .build()

            print("Asking player \"${it.name}\" at \"${it.url}\" \"${question.question}\".")

            val response = restTemplate.getForEntity(uri, String::class.java)

            when {
                response.statusCode.is2xxSuccessful -> {
                    when (response.body) {
                        question.answer -> {
                            println("Player \"${it.name}\" correctly answered \"${response.body}\". Scoring ${CORRECT_ANSWER_SCORE} points.")
                            playerService.changeScore(it, CORRECT_ANSWER_SCORE)
                            playerService.logResult(it, Log(question.question, question.answer, response.body!!, "CORRECT", CORRECT_ANSWER_SCORE))
                        }
                        else -> {
                            val incorrect_score = MAX_INCORRECT_ANSWER_SCORE / (index + 1)
                            println("Player \"${it.name}\" incorrectly answered \"${response.body}\". Scoring ${incorrect_score} points.")
                            playerService.changeScore(it, incorrect_score)
                            playerService.logResult(it, Log(question.question, question.answer, response.body!!, "INCORRECT", incorrect_score))
                        }
                    }
                }
                response.statusCode.is4xxClientError -> {
                    println("Player \"${it.name}\" responded with a 4XX status. Scoring ${STATUS_4XX_ERROR_SCORE} points.")
                    playerService.changeScore(it, STATUS_4XX_ERROR_SCORE)
                    playerService.logResult(it, Log(question.question, question.answer, "NONE", "ERROR_4XX", STATUS_4XX_ERROR_SCORE))
                }
                response.statusCode.is5xxServerError -> {
                    println("Player \"${it.name}\" responded with a 5XX status. Scoring ${STATUS_5XX_ERROR_SCORE} points.")
                    playerService.changeScore(it, STATUS_5XX_ERROR_SCORE)
                    playerService.logResult(it, Log(question.question, question.answer, "NONE", "ERROR_5XX", STATUS_5XX_ERROR_SCORE))
                }
            }
        }
    }
}