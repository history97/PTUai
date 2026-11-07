package kr.landscapebid.app.ui

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 지금 한국 시각 "YYYY-MM-DDTHH:MM". 공고 마감 시각(한국 시간)과 비교할 때 쓴다. */
fun nowKstIso(): String = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
