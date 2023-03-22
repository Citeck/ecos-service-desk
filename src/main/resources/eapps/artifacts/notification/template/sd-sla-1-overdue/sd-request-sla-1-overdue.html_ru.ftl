<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Истекает срок SLA 1. Необходимо взять в работу заявку <a href="${link.getRecordLink(docRef)}"
                                                            target="_blank">SD-${docNum}</a>.</p>

<@lib.reply_message/>
