<#macro reply_message>

    <#assign replyEmail = config.getNotNull("app/service-desk$sd-email-reply").asText()>

    <hr/>
    <i>Вы можете <a
            href="mailto:${replyEmail!""}?Subject=Re: ${_notification.title}"
            target="_top">Ответить</a> на это соощение по указанной ссылке.
        Ваш ответ будет добавлен как комментарий к заявке.</i>

</#macro>
