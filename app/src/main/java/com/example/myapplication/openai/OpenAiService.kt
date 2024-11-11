package com.example.myapplication.openai


import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

object OpenAIService {

    // Initialize OpenAI API client with your API key
    private val openAI = OpenAI(
        token = "api-key-input",
        timeout = Timeout(socket = 60.seconds),
    )

    fun batchMessages(messages: List<ChatMessage>, maxTokens: Int): List<List<ChatMessage>> {
        val batchedMessages = mutableListOf<List<ChatMessage>>()
        var currentBatch = mutableListOf<ChatMessage>()
        var currentTokenCount = 0

        for (message in messages) {
            val messageTokenCount = estimateTokenCount(message.content ?: "")

            // If adding this message exceeds the token limit, create a new batch
            if (currentTokenCount + messageTokenCount > maxTokens) {
                if (currentBatch.isNotEmpty()) {
                    batchedMessages.add(currentBatch)
                    currentBatch = mutableListOf()
                }
                currentTokenCount = 0 // Reset token count for the new batch
            }

            // Add the message to the current batch
            currentBatch.add(message)
            currentTokenCount += messageTokenCount
        }

        if (currentBatch.isNotEmpty()) {
            batchedMessages.add(currentBatch)
        }

        return batchedMessages
    }


    fun estimateTokenCount(text: String): Int {
        return (text.length / 4)
    }

    fun sendImageForAnalysis(base64Image: String, callback: (String) -> Unit) {

        //val imagePlaceholder =  "$base64Image"
        val imagePlaceholder =  "data:image/jpeg;base64,$base64Image"
        //val myImage = ImagePart("$imagePlaceholder")
        val messages = listOf(
            ChatMessage(ChatRole.System, "You are a helpful assistant that identifies attention-capture mechanisms in app screenshots"),
            ChatMessage(ChatRole.User, "Could you evaluate the following screenshot and identify attention-capture mechanisms in the app"),
            ChatMessage(ChatRole.User, "image_data: $imagePlaceholder")
        //TODO Send image to model(no idea for insert imageurl inside ChatMessage,gpt model cannot view base64 encoded image in the chat)
        )

        val batchedMessages = batchMessages(messages, 800) // Set according to model's max tokens

        runBlocking {
            val results = mutableListOf<String>()
            for (batch in batchedMessages) {
                val chatCompletionRequest = ChatCompletionRequest(
                    model = ModelId("gpt-4o-mini"),
                    messages = batch,
                    maxTokens = 400,
                    temperature = 0.5
                )

                try {
                    val completion = openAI.chatCompletion(chatCompletionRequest)
                    results.add(completion.choices[0].message.content ?: "No content returned")
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback("Error: ${e.message}")
                    return@runBlocking
                }
            }

            // Aggregate the results and pass to callback
            callback(results.joinToString(separator = "\n"))
        }
    }



}