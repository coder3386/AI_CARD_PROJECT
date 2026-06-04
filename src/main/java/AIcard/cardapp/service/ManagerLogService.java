package AIcard.cardapp.service;

import AIcard.cardapp.DTO.LogResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ManagerLogService {
    private final String LOG_BASE_DIR = "logs/";
    private final String ARCHIVE_DIR = "logs/archive/";

    /**
     * 로그 타입을 받아 해당 파일의 데이터를 DTO 리스트로 파싱 및 최신순 정렬하여 반환
     */
    public List<LogResponseDTO> getParsedLogs(String type, String date) {
        Path logPath;

        // 💡 날짜(date)가 지정되어 들어오면 archive 폴더의 과거 백업 파일을 조준합니다.
        if (date != null && !date.trim().isEmpty()) {
            String archiveFileName = determineArchiveFileName(type, date);
            logPath = Paths.get(ARCHIVE_DIR + archiveFileName);
        } else {
            // 날짜가 없으면 현재 활성화된 실시간 로그 파일 조준
            String fileName = determineFileName(type);
            logPath = Paths.get(LOG_BASE_DIR + fileName);
        }

        List<LogResponseDTO> parsedLogs = new ArrayList<>();
        if (!Files.exists(logPath)) {
            return parsedLogs;
        }

        try {
            List<String> allLines = Files.readAllLines(logPath);
            Collections.reverse(allLines);

            for (String line : allLines) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split("\\|");

                if ("error".equals(type)) {
                    if (parts.length < 5) continue; // 스택 트레이스 방어 차단
                    parsedLogs.add(LogResponseDTO.builder()
                            .timestamp(parts[0])
                            .thread(parts[1])
                            .level(parts[2])
                            .loggerName(parts[3])
                            .message(parts[4])
                            .build());
                } else if (parts.length >= 3) {
                    parsedLogs.add(LogResponseDTO.builder()
                            .timestamp(parts[0])
                            .thread(parts[1])
                            .message(parts[2])
                            .build());
                } else if (parts.length == 2) {
                    parsedLogs.add(LogResponseDTO.builder()
                            .timestamp(parts[0])
                            .message(parts[1])
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("로그 파일 파싱 중 에러 발생 (타입: {}, 날짜: {}): {}", type, date, e.getMessage());
        }

        return parsedLogs;
    }

    public List<String> getArchiveDates(String type) {
        TreeSet<String> dateSet = new TreeSet<>(Collections.reverseOrder()); // 최신 날짜가 위로 오도록 정렬
        File archiveFolder = new File(ARCHIVE_DIR);

        if (archiveFolder.exists() && archiveFolder.isDirectory()) {
            File[] files = archiveFolder.listFiles();
            if (files != null) {
                // 예: manager_activity.2026-06-01.log 패턴에서 날짜만 정규식으로 추출
                String prefix = determineFileName(type).replace(".log", "");
                Pattern pattern = Pattern.compile(prefix + "\\.(\\d{4}-\\d{2}-\\d{2})\\.log");

                for (File file : files) {
                    Matcher matcher = pattern.matcher(file.getName());
                    if (matcher.matches()) {
                        dateSet.add(matcher.group(1)); // "2026-06-01" 추출 완료
                    }
                }
            }
        }
        return new ArrayList<>(dateSet);
    }

    /**
     * 타입에 따른 로그 파일명 매핑 메서드
     */
    private String determineFileName(String type) {
        switch (type) {
            case "user":
                return "user_activity.log";
            case "access":
                return "card_access.log";
            case "error":
                return "system_error.log";
            default:
                return "manager_activity.log";
        }
    }


    private String determineArchiveFileName(String type, String date) {
        String prefix = determineFileName(type).replace(".log", "");
        return prefix + "." + date + ".log";
    }
}
