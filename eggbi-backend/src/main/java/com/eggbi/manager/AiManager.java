package com.eggbi.manager;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.eggbi.common.ErrorCode;
import com.eggbi.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对接qwen3.8-max模型的API接口
 */
@Service
public class AiManager {

    /**
     * AI 对话
     * @param message
     * @return
     */
    public String doChat(String message){
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey("accessKey")
                .baseUrl("url")
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(message)
                .model("qwen3.8-max")
                .build();

        try {
            ChatCompletion chatCompletion = client.chat().completions().create(params);
            System.out.println(chatCompletion);
            List<ChatCompletion.Choice> choices = chatCompletion.choices();
            ChatCompletion.Choice choice = choices.get(0);
            ChatCompletionMessage outPutMessage = choice.message();
            String content = outPutMessage.content().get();
            return content;
        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 响应错误");
        }
    }
}
