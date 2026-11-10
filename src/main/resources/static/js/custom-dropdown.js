function CustomDropdown(options) {
  const settings = options.settings || {};
  const events = options.events || {};

  // Get language from global variable or default to English
  const isFrench = typeof langSession !== "undefined" && langSession === "fr";

  // Default configuration
  const config = {
    select: options.select,
    multiSelect: false,
    searchMinChars: 2,
    debounceDelay: 300,
    hideSelected: settings.hideSelected !== false,
    keepOrder: settings.keepOrder !== false,
    closeOnSelect: settings.closeOnSelect !== false,
    placeholderText:
      settings.placeholderText ||
      (isFrench ? "Sélectionner une option" : "Select an option"),
    searchText:
      settings.searchText ||
      (isFrench ? "Aucun résultat trouvé" : "No results found"),
    searchPlaceholder:
      settings.searchPlaceholder || (isFrench ? "Recherche" : "Search"),
    searchingText:
      settings.searchingText ||
      (isFrench ? "Recherche en cours..." : "Searching..."),
    searchFunction: events.search,
    onChange: events.onChange,
  };

  const $originalSelect = $(config.select);

  if ($originalSelect.length === 0) {
    throw new Error("Target select element not found");
  }

  // Hide the original select
  $originalSelect.hide();

  // Create unique ID for this instance
  const instanceId =
    "custom-dropdown-" + Math.random().toString(36).substr(2, 9);

  // Create dropdown structure
  const $wrapper = $("<div>")
    .attr("id", instanceId + "-wrapper")
    .addClass("custom-select-wrapper");
  const $display = $("<div>")
    .addClass("custom-select-display")
    .attr("tabindex", "0");
  const $placeholder = $("<span>")
    .addClass("placeholder-text")
    .text(config.placeholderText);
  const $selectedText = $("<span>").addClass("selected-text").hide();
  const $clearBtn = $("<span>")
    .addClass("clear-selection")
    .html("&times;")
    .attr("title", "Clear")
    .hide();
  const $arrow = $("<span>").addClass("dropdown-arrow").html("&#9662;");

  const $dropdown = $("<div>").addClass("custom-dropdown").hide();
  const $searchContainer = $("<div>").addClass("search-container");
  const $searchInput = $("<input>").attr({
    type: "text",
    placeholder: config.searchPlaceholder,
    class: "search-input",
    autocomplete: "off",
  });
  const $statusContainer = $("<div>").addClass("status-container").hide();
  const $resultsContainer = $("<div>").addClass("results-container");

  // Assemble the structure
  $searchContainer.append($searchInput);
  $dropdown.append($searchContainer, $statusContainer, $resultsContainer);
  $display.append($placeholder, $selectedText, $clearBtn, $arrow);
  $wrapper.append($display, $dropdown);
  $originalSelect.after($wrapper);

  // State management
  let debounceTimer = null;
  let isDropdownOpen = false;
  let selectedValue = null;
  let lastSearchTerm = "";
  let currentResults = [];

  function updateDisplay() {
    if (!selectedValue) {
      $placeholder.show();
      $selectedText.hide().text("");
      $clearBtn.hide();
    } else {
      $placeholder.hide();
      $selectedText.show().text(selectedValue);
      $clearBtn.show();
    }

    // Update original select
    $originalSelect.empty();
    if (selectedValue) {
      $originalSelect.append(
        new Option(selectedValue, selectedValue, false, true),
      );
    }
    $originalSelect.trigger("change");
  }

  function showStatus(message, className = "") {
    const $statusMsg = $("<div>").addClass("status-message").text(message);
    if (className) {
      $statusMsg.addClass(className);
    }
    $statusContainer.empty().append($statusMsg).show();
  }

  function hideStatus() {
    $statusContainer.hide().empty();
  }

  function showResults(results) {
    $resultsContainer.empty();

    if (!results || results.length === 0) {
      return;
    }

    // Filter out selected items if hideSelected is true
    let availableResults = results;
    if (config.hideSelected && selectedValue) {
      availableResults = results.filter((item) => item !== selectedValue);
    }

    if (availableResults.length === 0) {
      const $noMore = $("<div>")
        .addClass("no-more-results")
        .text(
          isFrench ? "Option déjà sélectionnée" : "Option already selected",
        );
      $resultsContainer.append($noMore);
      return;
    }

    availableResults.forEach((item) => {
      const $option = $("<div>")
        .addClass("dropdown-option")
        .attr("data-value", item)
        .text(item);

      if (item === selectedValue) {
        $option.addClass("selected");
      }

      $resultsContainer.append($option);
    });
  }

  function performSearch(searchTerm) {
    // Handle empty search - show "No results found"
    if (!searchTerm || searchTerm.trim() === "") {
      showStatus(config.searchText, "no-results-status");
      $resultsContainer.empty();
      return;
    }

    // Handle minimum character requirement
    if (searchTerm.length < config.searchMinChars) {
      const minCharsError = isFrench
        ? "La recherche doit comporter au moins 2 caractères"
        : "Search must be at least 2 characters";
      showStatus(minCharsError, "warning-status");
      $resultsContainer.empty();
      return;
    }

    if (searchTerm === lastSearchTerm) {
      return;
    }

    lastSearchTerm = searchTerm;
    showStatus(config.searchingText, "loading-status");
    $resultsContainer.empty();

    // Call the search function
    if (config.searchFunction) {
      const currentData = selectedValue
        ? [{ value: selectedValue, text: selectedValue }]
        : [];

      const searchPromise = config.searchFunction(searchTerm, currentData);

      if (searchPromise && typeof searchPromise.then === "function") {
        searchPromise
          .then((options) => {
            currentResults = options.map(
              (option) => option.text || option.value || option,
            );

            if (currentResults.length === 0) {
              showStatus(config.searchText, "no-results-status");
              $resultsContainer.empty();
            } else {
              hideStatus();
              showResults(currentResults);
            }
          })
          .catch((error) => {
            console.error("Error in search function:", error);
            showStatus(
              typeof error === "string" ? error : config.searchText,
              "error-status",
            );
            $resultsContainer.empty();
          });
      }
    }
  }

  function openDropdown() {
    if (isDropdownOpen) return;

    isDropdownOpen = true;
    $dropdown.show();
    $arrow.addClass("open");

    setTimeout(() => {
      $searchInput.focus();
    }, 50);

    $searchInput.val("");
    lastSearchTerm = "";
    hideStatus();
    $resultsContainer.empty();
  }

  function closeDropdown() {
    if (!isDropdownOpen) return;

    isDropdownOpen = false;
    $dropdown.hide();
    $arrow.removeClass("open");
    $searchInput.val("");
    lastSearchTerm = "";
    hideStatus();
    $resultsContainer.empty();
  }

  function selectValue(value) {
    selectedValue = value;
    updateDisplay();

    if (config.onChange) {
      config.onChange(value);
    }

    closeDropdown();
  }

  function clearSelection() {
    selectedValue = null;
    updateDisplay();

    if (config.onChange) {
      config.onChange(null);
    }
  }

  // Event handlers
  $display.on("click", function (e) {
    if (!config.multiSelect && $(e.target).hasClass("clear-selection")) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    if (isDropdownOpen) {
      closeDropdown();
    } else {
      openDropdown();
    }
  });

  $clearBtn.on("click", function (e) {
    e.preventDefault();
    e.stopPropagation();
    clearSelection();
  });

  $searchInput.on("input", function () {
    const searchTerm = $(this).val().trim();
    clearTimeout(debounceTimer);

    const instance = publicAPI;
    clearTimeout(instance.debounceTimer);

    instance.debounceTimer = setTimeout(() => {
      performSearch(searchTerm);
    }, config.debounceDelay);
  });

  $searchInput.on("mousedown focus", function (e) {
    e.stopPropagation();
  });

  $searchInput.on("keydown", function (e) {
    if (e.key === "Escape") {
      e.preventDefault();
      closeDropdown();
    } else if (e.key === "Enter") {
      e.preventDefault();
      const $firstOption = $resultsContainer.find(".dropdown-option:first");
      if ($firstOption.length > 0) {
        $firstOption.trigger("click");
      }
    }
  });

  $resultsContainer.on("click", ".dropdown-option", function (e) {
    e.preventDefault();
    e.stopPropagation();
    const value = $(this).attr("data-value");
    console.log("=== Dropdown option clicked ===");
    console.log("Clicked value:", value);
    selectValue(value);
  });

  $(document).on("click", function (e) {
    if (!$(e.target).closest(`#${instanceId}-wrapper`).length) {
      closeDropdown();
    }
  });

  $dropdown.on("click", function (e) {
    e.stopPropagation();
  });

  $display.on("keydown", function (e) {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      if (isDropdownOpen) {
        closeDropdown();
      } else {
        openDropdown();
      }
    } else if (e.key === "Escape") {
      closeDropdown();
    }
  });

  // Initialize
  updateDisplay();

  const publicAPI = {
    setData: function (data) {
      currentResults = data || [];
    },
    setSelected: function (values) {
      if (Array.isArray(values) && values.length > 0) {
        selectedValue = values[0];
      } else if (typeof values === "string") {
        selectedValue = values;
      } else {
        selectedValue = null;
      }
      updateDisplay();
    },
    getSelected: function () {
      return selectedValue ? [selectedValue] : [];
    },
    close: function () {
      closeDropdown();
    },
    open: function () {
      openDropdown();
    },
    destroy: function () {
      clearTimeout(debounceTimer);
      $wrapper.remove();
      $originalSelect.show();
    },
    clearSelected: function () {
      selectedValue = null;
      updateDisplay();
    },
    debounceTimer: null,
  };

  return publicAPI;
}
