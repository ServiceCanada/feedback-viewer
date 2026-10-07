$(document).ready(function () {
  // Constants and Configuration
  const CONFIG = {
    DEBOUNCE_DELAY: 800,
    SEARCH_MIN_CHARS: 2,
    SPINNER_HIDE_DELAY: 1000,
    DATE_FORMAT: 'YYYY/MM/DD',
    BACKEND_DATE_FORMAT: 'YYYY-MM-DD'
  };

  const ENDPOINTS = {
    TOP_TASK_DATA: '/topTaskData',
    TOTAL_DISTINCT_TASKS: '/topTask/totalDistinctTasks',
    TOTAL_TASK_COUNT: '/topTask/totalTaskCount',
    DEPARTMENTS: '/topTaskSurvey/departments',
    EXPORT_CSV: '/exportTopTaskCSV',
    EXPORT_EXCEL: '/exportTopTaskExcel'
  };

  const MESSAGES = {
    fr: {
      ERROR_RETRIEVING_DATA: "Erreur lors de la récupération des données. Veuillez rafraîchir la page et réessayer.",
      NO_DATA_EXPORT: "Aucune donnée à exporter avec les filtres sélectionnés.",
      ERROR_CSV_DOWNLOAD: "Erreur lors du téléchargement du fichier CSV. Veuillez réessayer.",
      ERROR_EXCEL_DOWNLOAD: "Erreur lors du téléchargement du fichier Excel. Veuillez réessayer.",
      NETWORK_ERROR: "La réponse du réseau n'était pas correcte"
    },
    en: {
      ERROR_RETRIEVING_DATA: "Error retrieving data. Please refresh the page and try again.",
      NO_DATA_EXPORT: "No data to export with the selected filters.",
      ERROR_CSV_DOWNLOAD: "Error downloading CSV file. Please try again.",
      ERROR_EXCEL_DOWNLOAD: "Error downloading Excel file. Please try again.",
      NETWORK_ERROR: "Network response was not ok"
    }
  };

  var isFrench = langSession === "fr";
  var now = new Date();
  var formattedDate = now.getMonth() + 1 + "/" + now.getDate() + "/" + now.getFullYear();
  var loadingSpinner = $(".loading-spinner");

  // Initialize loading overlay
  var loadingOverlay = createDataTableLoadingOverlay(isFrench, 'spinner');

  // Show loading overlay immediately for initial table load
  loadingOverlay.show();

  // Utility functions
  function formatNumberWithCommas(number) {
    if (number == null || number === '') return number;
    return parseInt(number).toLocaleString();
  }

  function getMessage(key) {
    return MESSAGES[isFrench ? 'fr' : 'en'][key] || MESSAGES.en[key];
  }

  function showAlert(messageKey) {
    alert(getMessage(messageKey));
  }

  function handleError(error, messageKey, context = '') {
    console.error(`Error in ${context}:`, error);
    showAlert(messageKey);
    loadingSpinner.hide();
  }

  function debounce(func, delay) {
    let debounceTimer;
    return function () {
      const context = this;
      const args = arguments;
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => func.apply(context, args), delay);
    };
  }

  function createDownloadLink(blob, filename) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.style.display = 'none';
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  }

  function extractFilenameFromHeader(contentDisposition, defaultFilename) {
    if (contentDisposition && contentDisposition.indexOf('attachment') !== -1) {
      const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
      const matches = filenameRegex.exec(contentDisposition);
      if (matches && matches[1]) {
        return matches[1].replace(/['"]/g, '');
      }
    }
    return defaultFilename;
  }

  function getLastFiscalQuarter() {
    let today = moment();
    let fiscalYearStart = moment().month() < 3 ? moment().subtract(1, "year").month(3).startOf("month") : moment().month(3).startOf("month");
    let quarterStart, quarterEnd;

    if (today.isBetween(fiscalYearStart, fiscalYearStart.clone().add(2, "months").endOf("month"))) {
      quarterStart = fiscalYearStart.clone().subtract(1, "year").add(9, "months");
      quarterEnd = fiscalYearStart.clone().subtract(1, "day");
    } else if (today.isBefore(fiscalYearStart.clone().add(6, "months"))) {
      quarterStart = fiscalYearStart;
      quarterEnd = fiscalYearStart.clone().add(2, "months").endOf("month");
    } else if (today.isBefore(fiscalYearStart.clone().add(9, "months"))) {
      quarterStart = fiscalYearStart.clone().add(3, "months");
      quarterEnd = fiscalYearStart.clone().add(5, "months").endOf("month");
    } else {
      quarterStart = fiscalYearStart.clone().add(6, "months");
      quarterEnd = fiscalYearStart.clone().add(8, "months").endOf("month");
    }

    return [quarterStart, quarterEnd];
  }

  // ========================================
  // TaskAutocomplete - Client-side task filter
  // ========================================
  var TaskAutocomplete = (function() {
    var MAX_VISIBLE = 15;
    var allTasks = [];
    var selectedTask = null;
    var filteredTasks = [];
    var highlightIndex = -1;
    var isOpen = false;

    var $wrapper, $input, $clearBtn, $listbox;

    function init() {
      $wrapper  = $('.task-autocomplete-wrapper');
      $input    = $('#task-autocomplete-input');
      $clearBtn = $('.task-autocomplete-clear');
      $listbox  = $('#task-autocomplete-listbox');

      fetchTasks();
      bindEvents();
    }

    function fetchTasks() {
      fetch('/json/tasks.json')
        .then(function(response) {
          if (!response.ok) throw new Error('Failed to load tasks.json');
          return response.json();
        })
        .then(function(data) {
          if (Array.isArray(data) && data.length > 0 && Array.isArray(data[0].tasks)) {
            allTasks = data[0].tasks.filter(function(t) {
              return t && t.trim() !== '' && t.trim() !== '/' && t.indexOf('${') === -1;
            });
          }
        })
        .catch(function(err) {
          console.error('Error loading tasks.json:', err);
        });
    }

    function bindEvents() {
      $input.on('input', function() {
        var query = $input.val();
        if (selectedTask) {
          clearSelection(false);
        }
        if (query.trim().length === 0) {
          closeListbox();
          return;
        }
        filterAndRender(query);
      });

      $input.on('focus', function() {
        var query = $input.val().trim();
        if (query.length > 0 && !selectedTask) {
          filterAndRender(query);
        }
      });

      $input.on('keydown', function(e) {
        if (!isOpen) {
          if (e.key === 'ArrowDown' && $input.val().trim().length > 0) {
            filterAndRender($input.val());
            e.preventDefault();
          }
          return;
        }

        switch (e.key) {
          case 'ArrowDown':
            e.preventDefault();
            highlightIndex = Math.min(highlightIndex + 1, filteredTasks.length - 1);
            updateHighlight();
            scrollToHighlighted();
            break;
          case 'ArrowUp':
            e.preventDefault();
            highlightIndex = Math.max(highlightIndex - 1, 0);
            updateHighlight();
            scrollToHighlighted();
            break;
          case 'Enter':
            e.preventDefault();
            if (highlightIndex >= 0 && highlightIndex < filteredTasks.length) {
              selectTask(filteredTasks[highlightIndex]);
            }
            break;
          case 'Tab':
            if (highlightIndex >= 0 && highlightIndex < filteredTasks.length) {
              selectTask(filteredTasks[highlightIndex]);
            } else {
              closeListbox();
            }
            break;
          case 'Escape':
            e.preventDefault();
            closeListbox();
            $input.blur();
            break;
        }
      });

      $clearBtn.on('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        clearSelection(true);
        $input.focus();
      });

      $listbox.on('mousedown', 'li', function(e) {
        e.preventDefault();
        var index = $(this).data('index');
        if (index !== undefined && filteredTasks[index]) {
          selectTask(filteredTasks[index]);
        }
      });

      $listbox.on('mouseenter', 'li', function() {
        var idx = $(this).data('index');
        if (idx !== undefined) {
          highlightIndex = idx;
          updateHighlight();
        }
      });

      $(document).on('click', function(e) {
        if (!$(e.target).closest('.task-autocomplete-wrapper').length) {
          closeListbox();
        }
      });
    }

    function filterAndRender(query) {
      var lowerQuery = query.toLowerCase();
      filteredTasks = [];

      for (var i = 0; i < allTasks.length && filteredTasks.length < MAX_VISIBLE; i++) {
        if (allTasks[i].toLowerCase().indexOf(lowerQuery) !== -1) {
          filteredTasks.push(allTasks[i]);
        }
      }

      highlightIndex = filteredTasks.length > 0 ? 0 : -1;
      renderListbox();
      openListbox();
    }

    function renderListbox() {
      $listbox.empty();

      if (filteredTasks.length === 0) {
        var noResultsText = isFrench ? 'Aucun résultat trouvé' : 'No results found';
        $listbox.append(
          $('<li>').addClass('task-autocomplete-no-results')
                   .attr('role', 'option')
                   .attr('aria-disabled', 'true')
                   .text(noResultsText)
        );
      } else {
        filteredTasks.forEach(function(task, idx) {
          var $li = $('<li>')
            .attr('role', 'option')
            .attr('id', 'task-option-' + idx)
            .attr('data-index', idx)
            .text(task);
          if (idx === highlightIndex) {
            $li.addClass('task-autocomplete-highlighted')
               .attr('aria-selected', 'true');
          }
          $listbox.append($li);
        });
      }

      if (highlightIndex >= 0) {
        $input.attr('aria-activedescendant', 'task-option-' + highlightIndex);
      } else {
        $input.removeAttr('aria-activedescendant');
      }
    }

    function updateHighlight() {
      $listbox.find('li').removeClass('task-autocomplete-highlighted')
              .attr('aria-selected', 'false');
      if (highlightIndex >= 0) {
        var $target = $listbox.find('li[data-index="' + highlightIndex + '"]');
        $target.addClass('task-autocomplete-highlighted')
               .attr('aria-selected', 'true');
        $input.attr('aria-activedescendant', 'task-option-' + highlightIndex);
      } else {
        $input.removeAttr('aria-activedescendant');
      }
    }

    function scrollToHighlighted() {
      var $highlighted = $listbox.find('.task-autocomplete-highlighted');
      if ($highlighted.length) {
        var listboxEl = $listbox[0];
        var itemEl = $highlighted[0];
        if (itemEl.offsetTop < listboxEl.scrollTop) {
          listboxEl.scrollTop = itemEl.offsetTop;
        } else if (itemEl.offsetTop + itemEl.offsetHeight >
                   listboxEl.scrollTop + listboxEl.clientHeight) {
          listboxEl.scrollTop = itemEl.offsetTop + itemEl.offsetHeight
                                - listboxEl.clientHeight;
        }
      }
    }

    function openListbox() {
      if (isOpen) return;
      isOpen = true;
      $listbox.show();
      $wrapper.attr('aria-expanded', 'true');
    }

    function closeListbox() {
      if (!isOpen) return;
      isOpen = false;
      $listbox.hide();
      $wrapper.attr('aria-expanded', 'false');
      highlightIndex = -1;
      $input.removeAttr('aria-activedescendant');
    }

    function selectTask(taskName) {
      selectedTask = taskName;
      $input.val(taskName);
      $clearBtn.show();
      $input.addClass('has-selection');
      closeListbox();
      if (typeof table !== 'undefined') {
        table.ajax.reload();
      }
    }

    function clearSelection(clearInput) {
      selectedTask = null;
      if (clearInput !== false) {
        $input.val('');
      }
      $clearBtn.hide();
      $input.removeClass('has-selection');
      closeListbox();
      if (typeof table !== 'undefined') {
        table.ajax.reload();
      }
    }

    return {
      init: init,
      getSelected: function() {
        return selectedTask ? [selectedTask] : [];
      },
      reset: function() {
        clearSelection(true);
      }
    };
  })();

  TaskAutocomplete.init();

  var table = new DataTable("#topTaskTable", {
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
       autoWidth: false,
       dom: 't<"table-controls-outside"lip>',
       initComplete: function() {
         // Move pagination controls outside the table wrapper
         $('.table-controls-outside').insertAfter('.keywords-table-wrapper');
       },
       drawCallback: function () {
         fetchTotalDistinctTask();
         fetchTotalTaskCount();
       },
       buttons: [
         {
           extend: 'csvHtml5',
           className: 'btn btn-default',
           text: isFrench ? 'Télécharger CSV' : 'Download CSV',
           action: function (e, dt, button, config) {
             e.preventDefault();
             var url = new URL(window.location.origin + ENDPOINTS.EXPORT_CSV);
             url.search = getFilterParams().toString();
             window.location.href = url.toString();
           }
         },
         {
           extend: 'excelHtml5',
           className: 'btn btn-default',
           text: isFrench ? 'Télécharger Excel' : 'Download Excel',
           action: function (e, dt, button, config) {
             e.preventDefault();
             var url = new URL(window.location.origin + ENDPOINTS.EXPORT_EXCEL);
             url.search = getFilterParams().toString();
             window.location.href = url.toString();
           }
         }
       ],
       ajax: function(data, callback, settings) {
         loadingSpinner.show();

         // Debug logging for request construction
         console.log("=== DataTable Request Debug ===");
         console.log("Original DataTable params:", data);

         // Filter out null or empty params before setting them
         if ($("#department").val()) data.department = $("#department").val();
         if ($("#theme").val()) data.theme = $("#theme").val();
         var selectedTasks = TaskAutocomplete.getSelected();
         if (selectedTasks && selectedTasks.length > 0) {
            data.tasks = selectedTasks;
         }
         if ($("#group").val()) data.group = $("#group").val();
         if ($("#language").val()) data.language = $("#language").val();

         var dateRangePickerValue = $("#dateRangePicker").val();
         if (dateRangePickerValue) {
           var dateRange = $("#dateRangePicker").data("daterangepicker");
           data.startDate = dateRange.startDate.format(CONFIG.BACKEND_DATE_FORMAT);
           data.endDate = dateRange.endDate.format(CONFIG.BACKEND_DATE_FORMAT);
         } else {
           delete data.startDate;
           delete data.endDate;
         }
         data.taskCompletion = $("#taskCompletion").val();
         data.includeCommentsOnly = $("#commentsCheckbox").is(":checked");

         if ($("#comments").val()) data.comments = $("#comments").val();

         // Log final request data
         console.log("Final request params:", data);
         console.log("Tasks array:", data.tasks);
         console.log("Tasks array length:", data.tasks ? data.tasks.length : 0);

         // Calculate approximate URL length and determine method
         var paramString = $.param(data);
         var requestMethod = paramString.length > 2000 ? "POST" : "GET";
         console.log("Parameter string length:", paramString.length);
         console.log("Request method will be:", requestMethod);
         console.log("Full URL would be:", ENDPOINTS.TOP_TASK_DATA + "?" + paramString);

         if (paramString.length > 2000) {
           console.warn("WARNING: URL length exceeds 2000 characters, switching to POST");
         }

         // Make the AJAX request with dynamic method
         $.ajax({
           url: ENDPOINTS.TOP_TASK_DATA,
           type: requestMethod,
           data: data,
           success: function(response) {
             callback(response);
           },
           error: function(xhr, error, thrown) {
             console.error("=== DataTable AJAX Error ===");
             console.error("Status:", xhr.status);
             console.error("Status Text:", xhr.statusText);
             console.error("Response Text:", xhr.responseText);
             console.error("Error:", error);
             console.error("Thrown:", thrown);
             handleError({xhr, error, thrown}, 'ERROR_RETRIEVING_DATA', 'DataTable AJAX');
           },
           complete: function() {
             loadingSpinner.hide();
           }
         });
       },
       columns: [
         { data: 'dateTime', title: isFrench ? 'Date' : 'Date', visible: true, width: "10%", className: "dt-left" },
         { data: 'timeStamp', title: isFrench ? 'Horodatage' : 'Time Stamp', visible: false },
         { data: 'surveyReferrer', title: isFrench ? 'Référence de l\'enquête' : 'Survey Referrer', visible: false },
         { data: 'language', title: isFrench ? 'Langue' : 'Language', visible: false },
         { data: 'device', title: isFrench ? 'Appareil' : 'Device', visible: false },
         { data: 'screener', title: isFrench ? 'Écran' : 'Screener', visible: false },
         { data: 'dept', title: isFrench ? 'Ministère' : 'Department', visible: false },
         { data: 'theme', title: isFrench ? 'Thème' : 'Theme', visible: false },
         { data: 'themeOther', title: isFrench ? 'Autre thème' : 'Theme Other', visible: false },
         { data: 'grouping', title: isFrench ? 'Regroupement' : 'Grouping', visible: false },
         { data: 'task', title: isFrench ? 'Tâche' : 'Task', visible: true, width: "20%" },
         { data: 'taskOther', title: isFrench ? 'Autre tâche' : 'Task Other', visible: true, width: "15%" },
         { data: 'taskSatisfaction', title: isFrench ? 'Satisfaction de la tâche' : 'Task Satisfaction', visible: false },
         { data: 'taskEase', title: isFrench ? 'Facilité de la tâche' : 'Task Ease', visible: false },
         { data: 'taskCompletion', title: isFrench ? 'Accomplissement de la tâche' : 'Task Completion', visible: false },
         { data: 'taskImprove', title: isFrench ? 'Améliorer la tâche' : 'Task Improve', visible: false },
         { data: 'taskImproveComment', title: isFrench ? 'Améliorer la tâche - commentaire' : 'Task Improve Comment', visible: true, width: "27%" },
         { data: 'taskWhyNot', title: isFrench ? 'Pourquoi pas' : 'Task Why Not', visible: false },
         { data: 'taskWhyNotComment', title: isFrench ? 'Tâche non complétée - commentaire' : 'Task Why Not Comment', visible: true, width: "28%"},
         { data: 'taskSampling', title: isFrench ? 'Échantillonnage de tâche' : 'Task Sampling', visible: false },
         { data: 'samplingInvitation', title: isFrench ? 'Invitation à l\'échantillonnage' : 'Sampling Invitation', visible: false },
         { data: 'samplingGC', title: isFrench ? 'Échantillonnage GC' : 'Sampling GC', visible: false },
         { data: 'samplingCanada', title: isFrench ? 'Échantillonnage Canada' : 'Sampling Canada', visible: false },
         { data: 'samplingTheme', title: isFrench ? 'Thème d\'échantillonnage' : 'Sampling Theme', visible: false },
         { data: 'samplingInstitution', title: isFrench ? 'Institution d\'échantillonnage' : 'Sampling Institution', visible: false },
         { data: 'samplingGrouping', title: isFrench ? 'Regroupement d\'échantillonnage' : 'Sampling Grouping', visible: false },
         { data: 'samplingTask', title: isFrench ? 'Tâche d\'échantillonnage' : 'Sampling Task', visible: false }
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

     function fetchTotalDistinctTask() {
       fetch(ENDPOINTS.TOTAL_DISTINCT_TASKS)
         .then((response) => {
           if (!response.ok) {
             throw new Error(`HTTP error! status: ${response.status}`);
           }
           return response.text();
         })
         .then((totalDistinctTasks) => {
           $(".stat .totalDistinctTasks").text(formatNumberWithCommas(totalDistinctTasks));
         })
         .catch((err) => {
           console.warn("Error fetching total distinct tasks:", err);
         });
     }

  function fetchTotalTaskCount() {
    fetch(ENDPOINTS.TOTAL_TASK_COUNT)
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.text();
      })
      .then((totalTaskCount) => {
        $(".stat .totalTaskCount").text(formatNumberWithCommas(totalTaskCount));
      })
      .catch((err) => {
        console.warn("Error fetching total task count:", err);
      });
  }

  fetch(ENDPOINTS.DEPARTMENTS)
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return response.json();
    })
    .then(departments => {
      const departmentSelect = $("#department");
      departments.forEach(department => {
        departmentSelect.append(`<option value="${department.value}">${department.display}</option>`);
      });
    })
    .catch(err => {
      console.warn("Error fetching departments:", err);
    });

  $("#taskCompletion, #commentsCheckbox, #language").on("change", function () {
    table.ajax.reload();
  });

  //comments filter
    $("#comments, #url").on(
      "keyup",
      debounce(function (e) {
        table.ajax.reload();
      }, 800)
    );

  function resetFilters() {
    $("#department").val("");
    $("#theme").val("");
    $("#group").val("");
    $("#language").val("");
    $("#comments").val("");
    TaskAutocomplete.reset();
    $("#dateRangePicker").data("daterangepicker").setStartDate(moment(earliestDate));
    $("#dateRangePicker").data("daterangepicker").setEndDate(moment(latestDate));
    $("#dateRangePicker").val(earliestDate + " - " + latestDate);
    $("#commentsCheckbox").prop("checked", false);
    $("#taskCompletion").val("");
    table.ajax.reload();
  }

  $(".reset-filters").on("click", resetFilters);

  $("#dateRangePicker").daterangepicker(
    {
      opens: "left",
      startDate: moment(earliestDate),
      endDate: moment(latestDate),
      minDate: moment(earliestDate),
      maxDate: moment(latestDate),
      alwaysShowCalendars: true,
      locale: {
        format: CONFIG.DATE_FORMAT,
        cancelLabel: isFrench ? "Effacer" : "Clear",
        applyLabel: isFrench ? "Appliquer" : "Apply",
        customRangeLabel: isFrench ? "Période spécifique" : "Custom Range",
        firstDay: isFrench ? 1 : 0,
        daysOfWeek: isFrench ? ["Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"] : undefined,
        monthNames: isFrench ? ["Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"] : undefined,
      },
      ranges: {
        [isFrench ? "Toutes les dates" : "All Dates"]: [moment(earliestDate), moment(latestDate)],
        [isFrench ? "Aujourd'hui" : "Today"]: [moment(), moment()],
        [isFrench ? "Hier" : "Yesterday"]: [moment().subtract(1, "days"), moment().subtract(1, "days")],
        [isFrench ? "7 derniers jours" : "Last 7 Days"]: [moment().subtract(6, "days"), moment()],
        [isFrench ? "30 derniers jours" : "Last 30 Days"]: [moment().subtract(29, "days"), moment()],
        [isFrench ? "Ce mois-ci" : "This Month"]: [moment().startOf("month"), moment().endOf("month")],
        [isFrench ? "Le mois dernier" : "Last Month"]: [moment().subtract(1, "month").startOf("month"), moment().subtract(1, "month").endOf("month")],
        [isFrench ? "Dernier trimestre" : "Last Quarter"]: getLastFiscalQuarter(),
      },
    },
    function (start, end, label) {
      $("#dateRangePicker").val(start.format(CONFIG.DATE_FORMAT) + " - " + end.format(CONFIG.DATE_FORMAT));
      table.ajax.reload();
    }
  );

  $("#dateRangePicker").on("cancel.daterangepicker", function (ev, picker) {
    picker.setStartDate(moment(earliestDate));
    picker.setEndDate(moment(latestDate));
    $("#dateRangePicker").val(moment(earliestDate).format(CONFIG.DATE_FORMAT) + " - " + moment(latestDate).format(CONFIG.DATE_FORMAT));
    table.ajax.reload();
  });

  function handleDownload(url, defaultFilename, errorMessageKey) {
    loadingSpinner.show();
    
    fetch(url)
      .then(response => {
        if (response.status === 204) {
          loadingSpinner.hide();
          showAlert('NO_DATA_EXPORT');
          return null;
        }
        if (!response.ok) {
          throw new Error(`Export failed with HTTP ${response.status} ${response.statusText}`);
        }

        const disposition = response.headers.get('Content-Disposition');
        const filename = extractFilenameFromHeader(disposition, defaultFilename);
        // Returned so a failure while the body downloads (e.g. an aborted export) reaches the catch below
        return response.blob().then(blob => {
          createDownloadLink(blob, filename);
          setTimeout(() => {
            loadingSpinner.hide();
          }, CONFIG.SPINNER_HIDE_DELAY);
        });
      })
      .catch(error => {
        handleError(error, errorMessageKey, 'File download');
      });
  }

     $("#downloadCSV").on("click", function () {
       const url = new URL(window.location.origin + ENDPOINTS.EXPORT_CSV);
       url.search = getFilterParams().toString();
       handleDownload(url, 'top_task_survey_export.csv', 'ERROR_CSV_DOWNLOAD');
     });

     $("#downloadExcel").on("click", function () {
       const url = new URL(window.location.origin + ENDPOINTS.EXPORT_EXCEL);
       url.search = getFilterParams().toString();
       handleDownload(url, 'top_task_survey_export.xlsx', 'ERROR_EXCEL_DOWNLOAD');
     });
     tippy("#theme-tool-tip", {
       content: isFrench ? "Thèmes de navigation de Canada.ca " : "Canada.ca navigation themes ",
     });

     $("#department, #theme, #commentsCheckbox, #group, #language").on("change", function () {
       table.ajax.reload();
     });

     // Force recalculate column widths after window fully loads to prevent footer squishing
     $(window).on('load', function() {
       setTimeout(function() {
         table.columns.adjust().draw();
       }, 100);
     });

     function getFilterParams() {
       var params = new URLSearchParams();

       // Filter out null or empty params before setting them
       if ($("#department").val()) params.append('department', $("#department").val());
       if ($("#theme").val()) params.append('theme', $("#theme").val());
       if ($("#group").val()) params.append('group', $("#group").val());
       if ($("#language").val()) params.append('language', $("#language").val());
       if ($("#taskCompletion").val()) params.append("taskCompletion", $("#taskCompletion").val());
       params.append('includeCommentsOnly', $("#commentsCheckbox").is(":checked"));

       var selectedTasks = TaskAutocomplete.getSelected();
       if (selectedTasks && selectedTasks.length > 0) {
         selectedTasks.forEach(function(task) {
           params.append('tasks[]', task);
         });
       }

       var dateRangePickerValue = $("#dateRangePicker").val();
       if (dateRangePickerValue) {
         var dateRange = $("#dateRangePicker").data('daterangepicker');
         params.append('startDate', dateRange.startDate.format(CONFIG.BACKEND_DATE_FORMAT));
         params.append('endDate', dateRange.endDate.format(CONFIG.BACKEND_DATE_FORMAT));
       }

       return params;
     }

});
