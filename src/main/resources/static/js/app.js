// 공통 클라이언트 동작: CSRF 헤더, 공용 확인 대화상자, 버튼 aria-busy, 토스트 자동 닫기.
(function () {
  "use strict";

  function csrf() {
    var token = document.querySelector('meta[name="csrf-token"]');
    var header = document.querySelector('meta[name="csrf-header"]');
    return token && header ? { header: header.content, token: token.content } : null;
  }

  // htmx 요청에 CSRF 토큰을 붙인다.
  document.addEventListener("htmx:configRequest", function (evt) {
    var c = csrf();
    if (c) {
      evt.detail.headers[c.header] = c.token;
    }
  });

  // 비가역 작업은 공용 <dialog>로 확인한다(헌법 원칙 III, FR-008).
  document.addEventListener("htmx:confirm", function (evt) {
    var question = evt.detail.question;
    if (!question) {
      return;
    }
    evt.preventDefault();
    var dialog = document.getElementById("confirm-dialog");
    if (!dialog) {
      evt.detail.issueRequest(true);
      return;
    }
    dialog.querySelector("[data-confirm-message]").textContent = question;
    dialog.returnValue = "";
    dialog.addEventListener(
      "close",
      function () {
        if (dialog.returnValue === "confirm") {
          evt.detail.issueRequest(true);
        }
      },
      { once: true }
    );
    dialog.showModal();
  });

  // 100ms 이내 시각적 피드백: 요청 중인 버튼을 aria-busy로 표시한다.
  document.addEventListener("htmx:beforeRequest", function (evt) {
    var el = evt.detail.elt;
    var button = el.tagName === "FORM" ? el.querySelector('[type="submit"]') : el;
    if (button && button.tagName === "BUTTON") {
      button.setAttribute("aria-busy", "true");
      button.disabled = true;
    }
  });
  document.addEventListener("htmx:afterRequest", function (evt) {
    var el = evt.detail.elt;
    var button = el.tagName === "FORM" ? el.querySelector('[type="submit"]') : el;
    if (button && button.tagName === "BUTTON" && document.body.contains(button)) {
      button.removeAttribute("aria-busy");
      button.disabled = false;
    }
  });

  // 등록 성공(201) 후 추가 폼을 비운다.
  document.addEventListener("htmx:afterRequest", function (evt) {
    var form = evt.detail.elt;
    if (form.tagName !== "FORM" || !form.hasAttribute("data-reset-on-success")) {
      return;
    }
    if (evt.detail.xhr && evt.detail.xhr.status === 201) {
      form.reset();
      form.querySelectorAll('input[type="hidden"]').forEach(function (input) {
        if (input.name !== "_csrf") {
          input.value = "";
        }
      });
      var label = form.querySelector("[data-selected-security]");
      if (label) {
        label.textContent = label.getAttribute("data-empty-text") || "";
      }
      form.querySelectorAll('[id^="search-results-"]').forEach(function (el) {
        el.replaceChildren();
      });
    }
  });

  // 토스트는 4초 후 사라진다.
  document.addEventListener("htmx:oobAfterSwap", function () {
    document.querySelectorAll(".toast:not([data-timer])").forEach(function (toast) {
      toast.setAttribute("data-timer", "1");
      setTimeout(function () {
        toast.remove();
      }, 4000);
    });
  });

  // 가이드 진행 카드: 경과 시간에 맞춰 단계·진행 막대·경과 초를 갱신한다(서버 실제 단계가 아닌 소요 시간 기준 안내).
  function renderGuideProgress(card) {
    if (!card.dataset.startedAt) {
      var elapsedAtRender = Number(card.getAttribute("data-elapsed") || "0");
      card.dataset.startedAt = String(Date.now() - elapsedAtRender * 1000);
    }
    var elapsed = Math.max(0, (Date.now() - Number(card.dataset.startedAt)) / 1000);
    var expected = Number(card.getAttribute("data-expected") || "30");
    var steps = card.querySelectorAll(".analysis-steps li");
    var active = 0;
    steps.forEach(function (step, index) {
      if (elapsed >= Number(step.getAttribute("data-at") || "0")) {
        active = index;
      }
    });
    steps.forEach(function (step, index) {
      step.classList.toggle("is-done", index < active);
      step.classList.toggle("is-active", index === active);
      if (index === active) {
        step.setAttribute("aria-current", "step");
      } else {
        step.removeAttribute("aria-current");
      }
    });
    var bar = card.querySelector(".guide-progress-bar");
    if (bar) {
      // 예상 시간에 가까워질수록 느려지며 95%에서 멈춘다(완료는 결과 카드가 대신한다).
      bar.value = Math.round(95 * (1 - Math.exp(-elapsed / (expected / 2.5))));
    }
    var text = card.querySelector("[data-elapsed-text]");
    if (text) {
      text.textContent = String(Math.floor(elapsed));
    }
  }

  function renderAllGuideProgress() {
    document.querySelectorAll("[data-guide-progress]").forEach(renderGuideProgress);
  }

  document.addEventListener("htmx:afterSettle", renderAllGuideProgress);
  document.addEventListener("DOMContentLoaded", renderAllGuideProgress);
  setInterval(renderAllGuideProgress, 1000);

  // 종목 검색 결과 선택(키보드/마우스).
  document.addEventListener("click", function (evt) {
    var option = evt.target.closest("[data-security-id]");
    if (option) {
      selectSecurity(option);
    }
  });
  document.addEventListener("keydown", function (evt) {
    var option = evt.target.closest("[data-security-id]");
    if (option && (evt.key === "Enter" || evt.key === " ")) {
      evt.preventDefault();
      selectSecurity(option);
    }
  });

  function selectSecurity(option) {
    var form = option.closest("form");
    form.querySelector('input[name="securityId"]').value = option.dataset.securityId;
    form.querySelector('input[name="securityLabel"]').value = option.dataset.securityLabel;
    var label = form.querySelector("[data-selected-security]");
    label.textContent = option.dataset.securityLabel;
    var results = option.closest(".search-results");
    results.replaceChildren();
    var search = form.querySelector('input[name="q"]');
    search.value = option.dataset.securityLabel;
    form.querySelector('input[name="avgPrice"]').focus();
  }
})();
