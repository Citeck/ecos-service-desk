<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Работы по Вашему обращению <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> завершены.</p>
<p>Пожалуйста, подтвердите выполнение или верните заявку в работу.</p>
<p>Через 3 дня заявка будет закрыта автоматически.</p>

<@lib.reply_message/>
