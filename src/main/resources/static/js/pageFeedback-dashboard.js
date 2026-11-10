$(document).ready(function () {
  // Function to parse the query string and get the value of a specific parameter
  function getQueryParam(param) {
    var searchParams = new URLSearchParams(window.location.search);
    return searchParams.get(param);
  }

  function buildFilterQueryString() {
    const params = [];
    if ($("#section").val()) params.push("section=" + encodeURIComponent($("#section").val()));
    if ($("#theme").val()) params.push("theme=" + encodeURIComponent($("#theme").val()));
    if ($("#url").val()) params.push("url=" + encodeURIComponent($("#url").val()));
    if ($("#language").val()) params.push("language=" + encodeURIComponent($("#language").val()));
    if ($("#department").val()) params.push("department=" + encodeURIComponent($("#department").val()));
    if ($("#comments").val()) params.push("comments=" + encodeURIComponent($("#comments").val()));
    if ($("#errorComments").is(":checked")) params.push("error_keyword=true");

    // dateRangePicker: include startDate/endDate if present
    if ($("#dateRangePicker").length) {
      try {
        const dr = $("#dateRangePicker").data("daterangepicker");
        if (dr && dr.startDate && dr.endDate) {
          params.push("startDate=" + encodeURIComponent(dr.startDate.format("YYYY-MM-DD")));
          params.push("endDate=" + encodeURIComponent(dr.endDate.format("YYYY-MM-DD")));
        }
      } catch (e) {
        // ignore if dateRangePicker not available yet
      }
    }

    return params.length ? "?" + params.join("&") : "";
  }


  // Utility function to format numbers with comma separators
  function formatNumberWithCommas(number) {
    if (number == null || number === '') return number;
    return parseInt(number).toLocaleString();
  }

  // Check if the 'lang' query parameter is set to 'fr'
  var isFrench = langSession === "fr";
  var now = new Date();
  var formattedDate = now.getMonth() + 1 + "/" + now.getDate() + "/" + now.getFullYear();
  var formattedEarliestDate = moment(earliestDate).format("YYYY/MM/DD");
  var formattedLatestDate = moment(latestDate).format("YYYY/MM/DD");
  // Utility functions
  function debounce(func, delay) {
    let debounceTimer;
    return function () {
      const context = this;
      const args = arguments;
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => func.apply(context, args), delay);
    };
  }
  function downloadDashboardExport(endpoint) {
    var qs = buildFilterQueryString();
    window.location.href = endpoint + qs;
  }

  function resetFilters() {
    // Reset select elements to their default option (usually the first one)
    $("#department").val("");
    $("#language").val("");
    $("#errorComments").prop("checked", false);
    $("#theme").val("");
    $("#section").val("");
    // Clear text input fields
    $("#url").val("");
    $("#comments").val("");

    // Reset the Date Range Picker to the initial dates
    // Format the earliest and latest dates in YYYY/MM/DD format

    // Reset the Date Range Picker to the initial dates with formatted strings
    $("#dateRangePicker").data("daterangepicker").setStartDate(formattedEarliestDate);
    $("#dateRangePicker").data("daterangepicker").setEndDate(formattedLatestDate);

    // Update the display with formatted dates
    $("#dateRangePicker").val(formattedEarliestDate + " - " + formattedLatestDate);

    // Reload the DataTable to reflect the reset filters
    table.ajax.reload();
}

  function getLastFiscalQuarter() {
    let today = moment();
    let fiscalYearStart = moment().month() < 3 ? moment().subtract(1, "year").month(3).startOf("month") : moment().month(3).startOf("month"); // Adjust based on fiscal year starting in April
    let quarterStart, quarterEnd;

    // Determine the current fiscal quarter
    if (today.isBetween(fiscalYearStart, fiscalYearStart.clone().add(2, "months").endOf("month"))) {
      // Last quarter is Q4 of the previous fiscal year
      quarterStart = fiscalYearStart.clone().subtract(1, "year").add(9, "months");
      quarterEnd = fiscalYearStart.clone().subtract(1, "day");
    } else if (today.isBefore(fiscalYearStart.clone().add(6, "months"))) {
      // Last quarter is Q1
      quarterStart = fiscalYearStart;
      quarterEnd = fiscalYearStart.clone().add(2, "months").endOf("month");
    } else if (today.isBefore(fiscalYearStart.clone().add(9, "months"))) {
      // Last quarter is Q2
      quarterStart = fiscalYearStart.clone().add(3, "months");
      quarterEnd = fiscalYearStart.clone().add(5, "months").endOf("month");
    } else {
      // Last quarter is Q3
      quarterStart = fiscalYearStart.clone().add(6, "months");
      quarterEnd = fiscalYearStart.clone().add(8, "months").endOf("month");
    }

    return [quarterStart, quarterEnd];
  }

  // Initialize loading overlay
  var loadingOverlay = createDataTableLoadingOverlay(isFrench, 'spinner');

  // Show loading overlay immediately for initial table load
  loadingOverlay.show();

  // DataTable initialization
  var table = new DataTable("#myTable", {
    language: isFrench ? {
      url: "//cdn.datatables.net/plug-ins/2.3.2/i18n/fr-FR.json",
      lengthMenu: "Afficher _MENU_ entrées",
      info: "Affichage de _START_ à _END_ sur _TOTAL_ entrées",
      paginate: {
        first: "Premier",
        last: "Dernier",
        next: "Suivant",
        previous: "Précédent"
      }
    } : {
      lengthMenu: "Show _MENU_ entries",
      info: "Showing _START_ to _END_ of _TOTAL_ entries",
      paginate: {
        first: "First",
        last: "Last",
        next: "Next",
        previous: "Previous"
      }
    },
    stripeClasses: [],
    bSortClasses: false,
    order: [[0, "desc"]],
    processing: true,
    serverSide: true,
    retrieve: true,
    lengthMenu: [
      [10, 25, 50, 100],
      [10, 25, 50, 100],
    ],
    pageLength: 50,
    orderCellsTop: true,
    fixedHeader: false,
    responsive: false,
    drawCallback: function () {
      fetchTotalCommentsCount();
      fetchTotalPagesCount();
      fetchDataAndCreateChart();
    },
    dom: 't<"table-controls-outside"lip>',
    ajax: {
      url: "/dashboardData",
      type: "GET",
        dataSrc: function(json) {
          return json.data;
        },
      data: function (d) {
        d.language = $("#language").val();
        d.department = $("#department").val();

          var commentsVal = $("#comments").val();
          if (commentsVal && commentsVal.trim() !== "") {
            d.comments = commentsVal.trim();
          } else {
            delete d.comments; // Remove the filter from request
          }
        d.section = $("#section").val();
        d.theme = $("#theme").val();
        d.url = $("#url").val();
        if ($("#errorComments").prop("checked")) {
                  d.error_keyword = "true";  // Only send if checked
        }

        var dateRangePickerValue = $("#dateRangePicker").val();
        if (dateRangePickerValue) {
          var dateRange = $("#dateRangePicker").data("daterangepicker");
          d.startDate = dateRange.startDate.format("YYYY-MM-DD");
          d.endDate = dateRange.endDate.format("YYYY-MM-DD");
        } else {
          // If the date range picker is empty, do not send startDate and endDate in the request
          delete d.startDate; // Ensure startDate is not included in the AJAX request
          delete d.endDate; // Ensure endDate is not included in the AJAX request
        }

      },
      error: function (xhr, error, thrown) {
        alert(isFrench ? "Erreur lors de la récupération des données. Veuillez rafraîchir la page et réessayer." : "Error retrieving data. Please refresh the page and try again.");
        console.log("xhr: " + xhr);
        console.log("error: " + error);
        console.log("thrown : " + thrown);
      },
    },
    initComplete: function() {
      // Move pagination controls outside the table wrapper
      $('.table-controls-outside').insertAfter('.feedback-tool-data');
    },
    buttons: [
      {
        extend: "csvHtml5",
        className: "btn btn-default",
        action: function (e) {
          e.preventDefault();
          downloadDashboardExport('/dashboard/exportCSV');
        },
      },
      {
        extend: "excelHtml5",
        className: "btn btn-default",
        action: function (e) {
          e.preventDefault();
          downloadDashboardExport('/dashboard/exportExcel');
        },
      },
    ],
    columnDefs: SafeRender.columnDefs,
    columns: [
      { data: "institution" }, // Dept (visible in table)
      { data: "url", render: SafeRender.link },
      { 
        data: "urlEntries",
        render: function (data, type, row) {
          // Format numbers with comma separators for display
          if (type === 'display' || type === 'type') {
            return formatNumberWithCommas(data);
          }
          return data;
        }
      },
      { data: "language", visible: false }, // Language (hidden in table, but in CSV)
      { data: "section", visible: false }, // Section (hidden in table, but in CSV)
      { data: "theme", visible: false }, // Theme (hidden in table, but in CSV)
    ],
  });

  // Attach loading overlay to DataTable events
  attachLoadingOverlay(table, {
    loadingText: isFrench ? 'Chargement des données...' : 'Loading data...',
    subtext: isFrench ? 'Veuillez patienter pendant que nous filtrons vos résultats' : 'Please wait while we filter your results',
    spinnerType: 'spinner'
  });

  // Hide loading overlay after initial table draw
  table.on('draw.dt', function() {
    loadingOverlay.hide();
  });

  function fetchTotalCommentsCount() {
    const qs = buildFilterQueryString();
    fetch("/pageFeedback/totalCommentsCount" + qs, { cache: "no-store" })
      .then((response) => {
        if (!response.ok) throw new Error("Network response was not ok: " + response.status);
        return response.text();
      })
      .then((totalCommentsCount) => {
        // Update the total comments count in the <span class="number"> element with comma formatting
        $(".stat .totalCommentCount").text(formatNumberWithCommas(totalCommentsCount));
      })
      .catch((err) => {
        console.warn("Something went wrong.", err);
      });
  }

  function fetchTotalPagesCount() {
    const qs = buildFilterQueryString();
    fetch("/pageFeedback/totalPagesCount" + qs, { cache: "no-store" })
      .then((response) => {
        if (!response.ok) throw new Error("Network response was not ok: " + response.status);
        return response.text();
      })
      .then((totalPagesCount) => {
        // Update the total pages count in the <span class="number"> element with comma formatting
        $(".stat .totalPagesCount").text(formatNumberWithCommas(totalPagesCount));
      })
      .catch((err) => {
        console.warn("Something went wrong.", err);
      });
  }

  $(".reset-filters").on("click", function () {
    resetFilters();
  });

  $("#dateRangePicker").daterangepicker(
    {
      opens: "left",
      startDate: moment(earliestDate),
      endDate: moment(latestDate),
      minDate: moment(earliestDate), // Set the earliest selectable date
      maxDate: moment(latestDate),
      alwaysShowCalendars: true,
      locale: {
        format: "YYYY/MM/DD",
        cancelLabel: isFrench ? "Effacer" : "Clear",
        applyLabel: isFrench ? "Appliquer" : "Apply",
        customRangeLabel: isFrench ? "Période spécifique" : "Custom Range",
        firstDay: isFrench ? 1 : 0, // Start with Monday
        daysOfWeek: isFrench ? ["Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"] : undefined, // Define days for French
        monthNames: isFrench ? ["Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"] : undefined, // Define months for French
      },
      ranges: {
        [isFrench ? "Toutes les dates" : "All Dates"]: [moment(earliestDate), moment(latestDate)],
        [isFrench ? "Hier" : "Yesterday"]: [moment().subtract(1, "days"), moment().subtract(1, "days")],
        [isFrench ? "7 derniers jours" : "Last 7 Days"]: [moment().subtract(7, "days"), moment()],
        [isFrench ? "30 derniers jours" : "Last 30 Days"]: [moment().subtract(30, "days"), moment()],
        [isFrench ? "Ce mois-ci" : "This Month"]: [moment().startOf("month"), moment().endOf("month")],
        [isFrench ? "Le mois dernier" : "Last Month"]: [moment().subtract(1, "month").startOf("month"), moment().subtract(1, "month").endOf("month")],
        [isFrench ? "Dernier trimestre" : "Last Quarter"]: getLastFiscalQuarter(),
      },
    },
    function (start, end, label) {
      $("#dateRangePicker").val(start.format("YYYY/MM/DD") + " - " + end.format("YYYY/MM/DD"));
      // Reload the DataTable with the new date range from the input value.
      table.ajax.reload();
    }
  );

  $("#dateRangePicker").on("cancel.daterangepicker", function (ev, picker) {
    // Set the date range picker to the earliest and latest dates
    picker.setStartDate(moment(earliestDate));
    picker.setEndDate(moment(latestDate));
    // Update the input field to show the earliest and latest dates
    $("#dateRangePicker").val(formattedEarliestDate + " - " + formattedLatestDate);
    // Reload DataTables to reflect the reset date range
    table.ajax.reload();
  });

  $("#downloadCSV").on("click", function () {
    downloadDashboardExport('/dashboard/exportCSV');
  });

  $("#downloadExcel").on("click", function () {
    downloadDashboardExport('/dashboard/exportExcel');
  });

  //  $(document).on("click", "a[href*='design.canada.ca'], a[href*='conception.canada.ca']", function (e) {
  //    e.preventDefault(); // Prevent the default link behavior
  //    window.open($(this).attr("href"), "_blank"); // Open the link in a new tab/window
  //  });

  tippy("#section-tool-tip", {
    content: isFrench ? "Une valeur ajoutée manuellement à certaines pages" : "A value manually added to select pages",
  });

  tippy("#theme-tool-tip", {
    content: isFrench ? "Thèmes de navigation de Canada.ca " : "Canada.ca navigation themes ",
  });
  function calculateRollingAverage(data, windowSize) {
    let rollingAverages = [];
    for (let i = 0; i <= data.length - windowSize; i++) {
        let windowData = data.slice(i, i + windowSize);
        let windowSum = windowData.reduce((sum, value) => sum + value, 0);
        let average = windowSum / windowSize;
        rollingAverages.push(parseInt(average));
    }
    return rollingAverages;
}
let chartInstance = null;

function fetchDataAndCreateChart() {
  const errorKeywordChecked = $("#errorComments").prop("checked");
  let url = "/chartData";
  let params = [];

  if (errorKeywordChecked) params.push("error_keyword=true");

  const dateRangePickerValue = $("#dateRangePicker").val();
  if (dateRangePickerValue) {
    const dateRange = $("#dateRangePicker").data("daterangepicker");
    params.push("startDate=" + encodeURIComponent(dateRange.startDate.format("YYYY-MM-DD")));
    params.push("endDate=" + encodeURIComponent(dateRange.endDate.format("YYYY-MM-DD")));
  }

  if ($("#language").val()) params.push("language=" + encodeURIComponent($("#language").val()));
  if ($("#department").val()) params.push("department=" + encodeURIComponent($("#department").val()));
  if ($("#comments").val()) params.push("comments=" + encodeURIComponent($("#comments").val()));
  if ($("#section").val()) params.push("section=" + encodeURIComponent($("#section").val()));
  if ($("#theme").val()) params.push("theme=" + encodeURIComponent($("#theme").val()));
  if ($("#url").val()) params.push("url=" + encodeURIComponent($("#url").val()));

  if (params.length > 0) url += "?" + params.join("&");

  fetch(url)
    .then((response) => response.json())
    .then((data) => {
      const categories = data.map((item) => item.date);
      const commentsData = data.map((item) => item.comments);

      const windowSize = 7;
      const rollingAverages = calculateRollingAverage(commentsData, windowSize);
      const paddedRollingAverages = new Array(windowSize - 1).fill(null).concat(rollingAverages);

      if (chartInstance) {
        chartInstance.destroy();
      }

      const ctx = document.getElementById("chartCanvas").getContext("2d");
      chartInstance = new Chart(ctx, {
        data: {
          labels: categories,
          datasets: [
            {
              type: "bar",
              label: isFrench ? "Commentaires" : "Comments",
              data: commentsData,
              backgroundColor: "#2a78d6",       // categorical slot 1 (blue)
              hoverBackgroundColor: "#2a78d6",
              borderWidth: 0,
              borderRadius: 4,
              borderSkipped: "bottom",
              maxBarThickness: 28,
              categoryPercentage: 0.9,
              barPercentage: 0.9,
              order: 2,
            },
            {
              type: "line",
              label: isFrench ? "Moyenne mobile (7 jours)" : "Rolling Average (7 days)",
              data: paddedRollingAverages,
              borderColor: "#eb6834",           // categorical slot 8 (orange)
              backgroundColor: "#eb6834",
              borderWidth: 5,
              pointRadius: 0,
              pointHoverRadius: 4,
              pointHoverBorderWidth: 0,
              spanGaps: true,
              tension: 0.35,
              order: 1,
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          layout: { padding: { top: 4, right: 8 } },
          interaction: {
            mode: "index",
            intersect: false,
          },
          plugins: {
            title: {
              display: true,
              text: isFrench ? "Commentaires par jour" : "Comments by day",
              align: "start",
              font: { size: 18, weight: "600" },
              color: "#0b0b0b",                 // primary ink
              padding: { bottom: 18 },
            },
            legend: {
              position: "bottom",
              labels: {
                font: { size: 13 },
                color: "#52514e",               // secondary ink
                usePointStyle: true,
                pointStyle: "circle",
                boxWidth: 8,
                boxHeight: 8,
                padding: 18,
              },
            },
            tooltip: {
              backgroundColor: "#ffffff",
              titleColor: "#0b0b0b",
              bodyColor: "#52514e",
              borderColor: "rgba(11,11,11,0.10)",
              borderWidth: 1,
              padding: 12,
              cornerRadius: 6,
              usePointStyle: true,
              boxPadding: 6,
              callbacks: {
                label: function (context) {
                  const val = context.parsed.y;
                  if (val === null) return null;
                  const suffix = isFrench ? " commentaires" : " comments";
                  return context.dataset.label + ": " + formatNumberWithCommas(val) + suffix;
                },
              },
            },
          },
          scales: {
            x: {
              ticks: {
                font: { size: 12 },
                color: "#898781",               // muted axis ink
                maxRotation: 45,
                autoSkipPadding: 12,
              },
              grid: { display: false },
              border: { color: "#c3c2b7" },      // baseline
            },
            y: {
              min: 0,
              title: {
                display: true,
                text: isFrench ? "Nombre de commentaires" : "Number of Comments",
                font: { size: 13, weight: "600" },
                color: "#52514e",
              },
              ticks: {
                font: { size: 12 },
                color: "#898781",
                padding: 8,
                callback: function (value) { return formatNumberWithCommas(value); },
              },
              grid: { color: "#e1e0d9", drawTicks: false },   // hairline gridlines
              border: { display: false },
            },
          },
        },
      });
    })
    .catch((error) => {
      console.error("Error fetching data: ", error);
    });
}


  var detailsElement = $("#filterDetails");
  var summaryElement = $("#filterSummary");

  detailsElement.on("toggle", function () {
    if (detailsElement.prop("open")) {
      summaryElement.text(isFrench ? "Voir moins de filtres" : "See less filters");
    } else {
      summaryElement.text(isFrench ? "Voir plus de filtres" : "See more filters");
    }
  });

  $("#language, #department, #section, #theme").on("change", function () {
    table.ajax.reload();
  });

  // Handle error comments checkbox
    $("#errorComments").on("change", function () {
      const $label = $(this).closest('label');
      if ($(this).is(':checked')) {
        $label.addClass('active');
      } else {
        $label.removeClass('active');
      }
      table.ajax.reload();

    });

  $("#comments, #url").on(
    "keyup",
    debounce(function (e) {
         table.ajax.reload(); // Reload the table without resetting pagination
    }, 800)
  );

  // Force recalculate column widths after window fully loads to prevent footer squishing
  $(window).on('load', function() {
    setTimeout(function() {
      table.columns.adjust().draw();
    }, 100);
  });

});

