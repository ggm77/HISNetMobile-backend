package com.seohamin.hisnetmobile.domain.student.controller;

import com.seohamin.hisnetmobile.domain.student.dto.StudentInfoResponseDto;
import com.seohamin.hisnetmobile.domain.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    // 현재 로그인한 사용자의 학적 기본사항 조회 API
    @GetMapping("/me")
    public ResponseEntity<StudentInfoResponseDto> getMyInfo(
            @AuthenticationPrincipal final UserDetails userDetails
    ) {

        return ResponseEntity.ok(studentService.getMyInfo(userDetails));
    }
}