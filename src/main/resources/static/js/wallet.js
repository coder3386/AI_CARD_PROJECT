document.addEventListener('DOMContentLoaded', () => {
    const wallet = document.getElementById('walletWrapper');
    const overlay = document.getElementById('screenOverlay');

    let isDragging = false;
    let active = false;
    let startX, startY;

    // 클릭 이벤트
    wallet.addEventListener('click', (e) => {
        // 드래그 직후 발생하는 클릭 이벤트 방지
        if (isDragging) return;

        active = !active;
        if (active) {
            wallet.classList.add('active');
            overlay.style.display = 'block';
        } else {
            wallet.classList.remove('active');
            overlay.style.display = 'none';
        }
    });

    // 드래그 로직 (아이콘이 활성화된 상태에서만 가능)
    wallet.onmousedown = function (event) {
        if (!active) return;

        isDragging = false; // 초기화
        let shiftX = event.clientX - wallet.getBoundingClientRect().left;
        let shiftY = event.clientY - wallet.getBoundingClientRect().top;

        function moveAt(pageX, pageY) {
            isDragging = true; // 마우스를 움직이면 드래그 상태로 간주
            wallet.style.left = pageX - shiftX + 'px';
            wallet.style.top = pageY - shiftY + 'px';
            wallet.style.bottom = 'auto';
            wallet.style.right = 'auto';
        }

        function onMouseMove(event) {
            moveAt(event.pageX, event.pageY);
        }

        document.addEventListener('mousemove', onMouseMove);

        document.onmouseup = function () {
            document.removeEventListener('mousemove', onMouseMove);
            document.onmouseup = null;
            // 약간의 딜레이를 주어 드래그 종료 후 클릭 이벤트가 바로 발생하지 않게 함
            setTimeout(() => {
                isDragging = false;
            }, 100);
        };
    };

    wallet.ondragstart = function () {
        return false;
    };
});