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

  // 금액 입력(data-thousands): 입력하는 동안 정수부에 천 단위 콤마를 넣는다. 소수점 이하는 그대로 둔다.
  // 서버(HoldingForm)는 콤마를 지우고 해석한다. 커서는 같은 숫자 뒤에 오도록 되돌린다.
  function groupThousands(raw) {
    var clean = raw.replace(/[^\d.]/g, "");
    var dot = clean.indexOf(".");
    var intPart = dot >= 0 ? clean.slice(0, dot) : clean;
    var frac = dot >= 0 ? "." + clean.slice(dot + 1).replace(/\./g, "") : "";
    return intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",") + frac;
  }

  function formatThousands(input) {
    var raw = input.value;
    var next = groupThousands(raw);
    if (next === raw) {
      return;
    }
    var caret = input.selectionStart === null ? raw.length : input.selectionStart;
    var significant = raw.slice(0, caret).replace(/[^\d.]/g, "").length;
    input.value = next;
    if (document.activeElement !== input) {
      return;
    }
    var pos = 0;
    for (var seen = 0; pos < next.length && seen < significant; pos++) {
      if (next[pos] !== ",") {
        seen++;
      }
    }
    input.setSelectionRange(pos, pos);
  }

  function formatAllThousands() {
    document.querySelectorAll("input[data-thousands]").forEach(formatThousands);
  }

  document.addEventListener("input", function (evt) {
    if (evt.target.matches && evt.target.matches("input[data-thousands]")) {
      formatThousands(evt.target);
    }
  });
  document.addEventListener("htmx:afterSettle", formatAllThousands);
  document.addEventListener("DOMContentLoaded", formatAllThousands);

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

  // AI 가이드 서랍 패널: 가이드 응답이 #guide-panel에 들어오면 연다. 닫으면 포커스를 연 버튼으로 돌려준다.
  var drawerOpener = null;
  var drawerOpenerId = null;

  /** 연 버튼으로 포커스를 돌려준다. 버튼이 다시 그려졌으면(한도 갱신) 같은 가이드 버튼 묶음 id로 새 버튼을 찾는다. */
  function restoreDrawerFocus() {
    var target = drawerOpener;
    if (!target || !document.body.contains(target)) {
      var wrapper = drawerOpenerId ? document.getElementById(drawerOpenerId) : null;
      target = wrapper ? wrapper.querySelector("button") : null;
    }
    if (target) {
      target.focus();
    }
  }

  function guideDrawer() {
    return document.getElementById("guide-drawer");
  }

  function openGuideDrawer(opener) {
    var drawer = guideDrawer();
    if (!drawer) {
      return;
    }
    if (opener) {
      drawerOpener = opener;
      var wrapper = opener.closest("[id^='guide-btn-']");
      drawerOpenerId = wrapper ? wrapper.id : null;
    }
    if (!drawer.open) {
      drawer.showModal();
    }
  }

  document.addEventListener("htmx:afterSwap", function (evt) {
    if (evt.detail.target && evt.detail.target.id === "guide-panel" && evt.detail.requestConfig) {
      var elt = evt.detail.requestConfig.elt;
      // 패널 안의 폴링 교체는 열려 있을 때만 의미가 있으므로, 사용자가 요청한 경우에만 연다.
      if (elt && !elt.closest("#guide-panel")) {
        openGuideDrawer(elt.closest("button") || elt);
      }
    }
  });

  document.addEventListener("click", function (evt) {
    var drawer = guideDrawer();
    if (!drawer) {
      return;
    }
    if (evt.target.closest("[data-close-drawer]") || evt.target === drawer) {
      drawer.close();
    }
  });

  document.addEventListener(
    "close",
    function (evt) {
      if (evt.target.id === "guide-drawer") {
        restoreDrawerFocus();
      }
    },
    true
  );

  // 패널이 닫혀 있을 때 가이드가 끝나면 토스트로 알리고 "보기"로 다시 연다(guide-ready는 HX-Trigger 이벤트).
  document.addEventListener("guide-ready", onGuideReady);

  function onGuideReady(evt) {
    var drawer = guideDrawer();
    var template = document.getElementById("guide-ready-toast");
    var region = document.getElementById("toast-region");
    if (!drawer || drawer.open || !template || !region) {
      return;
    }
    var detail = evt.detail || {};
    var source = document.querySelector('[data-security-id="' + detail.securityId + '"]');
    var toast = template.content.firstElementChild.cloneNode(true);
    toast.querySelector("[data-guide-name]").textContent = source ? source.getAttribute("data-security-name") : "";
    toast.querySelector(detail.ok ? "[data-guide-failed]" : "[data-guide-ok]").remove();
    toast.querySelector("[data-guide-view]").addEventListener("click", function () {
      toast.remove();
      var opener = source ? source.querySelector("button") : null;
      htmx.ajax("GET", "/guides/" + detail.id + "/card", { target: "#guide-panel", swap: "innerHTML" }).then(function () {
        openGuideDrawer(opener);
      });
    });
    region.appendChild(toast);
    setTimeout(function () {
      toast.remove();
    }, 15000);
  }

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
