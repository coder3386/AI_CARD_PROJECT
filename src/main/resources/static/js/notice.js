let unreadNotices = []; // 공지를 담아둘 배열
let currentUserId = null;

document.addEventListener('DOMContentLoaded', function() {
    console.log("공지사항 스크립트 로드됨");

    // 로그인 확인
    if (!window.loggedInUserId) {
        console.log("비로그인 상태입니다.");
        return;
    }
    currentUserId = window.loggedInUserId;

    // 공지사항 조회 API 호출
    fetch('/card/api/notices/active?userId=' + currentUserId)
        .then(response => response.json())
        .then(notices => {
            console.log("받아온 공지사항 데이터:", notices);

            // 공지가 하나라도 있으면 프로세스 시작
            if (notices && notices.length > 0) {
                unreadNotices = notices;
                showNextNotice(); // 첫 번째 팝업 띄우기 시작
            }
        })
        .catch(error => console.error('로드 오류:', error));
});

// 💡 다음 공지를 띄우는 핵심 함수
function showNextNotice() {
    if (unreadNotices.length > 0) {
        let notice = unreadNotices.shift(); // 배열에서 하나를 꺼냄
        showNoticeModal(notice);
    } else {
        console.log("모든 공지 확인 완료");
    }
}

function showNoticeModal(notice) {
    const modal = document.getElementById('noticeModal');
    document.getElementById('noticeTitle').innerText = notice.title;
    document.getElementById('noticeContent').innerHTML = notice.content;

    modal.style.display = 'flex';

    document.getElementById('closeNoticeBtn').onclick = function() {
        // 읽음 처리 요청
        fetch('/card/api/notices/read', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ noticeId: notice.noticeId, userId: currentUserId })
        })
            .then(response => {
                if(response.ok) {
                    modal.style.display = 'none';
                    // 팝업 닫은 후, 다음 공지가 있는지 확인하고 띄우기 (재귀 호출)
                    showNextNotice();
                }
            });
    };
}