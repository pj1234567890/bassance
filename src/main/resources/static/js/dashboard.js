let csrfToken;

document.getElementById("logoutButton").addEventListener("click", logout);
document.getElementById("uploadButton").addEventListener("click", upload);

async function initialize() {
    try {
        const csrfResponse = await fetch("/auth/csrf");
        if (!csrfResponse.ok) throw new Error("Could not initialize dashboard");
        csrfToken = (await csrfResponse.json()).token;
        const response = await fetch("/tracks");
        if (response.status === 401 || response.status === 403) {
            window.location.href = "login.html";
            return;
        }
        await loadTracks(response);
    } catch {
        document.getElementById("uploadStatus").textContent = "Could not load your tracks. Please refresh.";
    }
}

async function logout() {
    await fetch("/auth/logout", {
        method: "POST",
        headers: { "X-XSRF-TOKEN": csrfToken }
    });
    window.location.href = "login.html";
}

async function upload() {
    const fileInput = document.getElementById("file");
    const status = document.getElementById("uploadStatus");
    const file = fileInput.files[0];

    if (!file) {
        status.textContent = "Select an audio file first.";
        return;
    }
    if (file.size > 50 * 1024 * 1024) {
        status.textContent = "The audio file must be 50 MB or smaller.";
        return;
    }

    const form = new FormData();
    form.append("file", file);
    status.textContent = "Uploading and analyzing...";

    try {
        const response = await fetch("/tracks/upload", {
            method: "POST",
            headers: { "X-XSRF-TOKEN": csrfToken },
            body: form
        });
        if (!response.ok) {
            status.textContent = response.status === 429
                ? "Upload limit reached. Please wait before trying again."
                : response.status === 413
                    ? "The audio file must be 50 MB or smaller."
                    : response.status === 415
                        ? "Choose a valid MP3, WAV, FLAC, OGG, or M4A file."
                        : "Upload or analysis failed. Please try again.";
            return;
        }
        status.textContent = "Analysis complete.";
        fileInput.value = "";
        await loadTracks();
    } catch {
        status.textContent = "The audio service is temporarily unavailable.";
    }
}

async function loadTracks(response) {
    const result = response || await fetch("/tracks");
    if (!result.ok) return;

    const tracks = await result.json();
    const body = document.getElementById("tracksBody");
    body.replaceChildren();

    for (const track of tracks) {
        const row = document.createElement("tr");
        const nameCell = document.createElement("td");
        const nameInput = document.createElement("input");
        nameInput.maxLength = 100;
        nameInput.value = track.displayName || "";
        nameInput.addEventListener("change", () => rename(track.id, nameInput.value));
        nameCell.appendChild(nameInput);

        const genresCell = document.createElement("td");
        genresCell.className = "genre";
        genresCell.textContent = track.genresJson || "";
        const timeCell = document.createElement("td");
        timeCell.textContent = track.createdAt || "";

        row.append(nameCell, genresCell, timeCell);
        body.appendChild(row);
    }
}

async function rename(id, displayName) {
    await fetch(`/tracks/${encodeURIComponent(id)}/rename`, {
        method: "PUT",
        headers: {
            "X-XSRF-TOKEN": csrfToken,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ displayName })
    });
}

initialize();
