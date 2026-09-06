package com.seohamin.hisnetmobile.domain.student.dto;

import java.util.Map;

/**
 * 학적 기본정보(HHAK110M.php, "기본정보관리") 조회 응답.
 * <p>
 * 원본은 {@code form[name=form1]} 안의 표 하나로 되어 있고, 라벨 셀은 {@code td.tblcationTitlecls},
 * 값은 대부분 옆 셀의 평문이며 성명(영문)/휴대폰/전화번호/이메일 4개만 {@code <input value>} 로 들어있다.
 * 자주 쓰는 항목은 타입을 지정해 노출하고, 파싱된 라벨-값 쌍 전체는 {@code raw} 에 담는다.
 * 날짜류는 원본 표기가 제각각(예: {@code 20230227}, {@code 04-08-06})이라 문자열 그대로 둔다.
 */
public record StudentInfoResponseDto(
        String studentNo,
        String name,
        String nameEnglish,
        String academicStatus,
        String grade,
        String nationality,
        String birthDate,
        String curriculumType,
        String admissionDate,
        String highSchool,
        String graduationDate,
        String degreeNumber,
        String department,
        String major,
        String doubleMajor,
        String minor,
        String engineeringCertification,
        String combinedDegree,
        String practicalComputing,
        String rcInfo,
        String mobile,
        String phone,
        String email,
        String address,
        Map<String, String> raw
) { }