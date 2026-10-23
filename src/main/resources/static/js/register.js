document.getElementById("registerButton").addEventListener("click", async () => {
    const error = document.getElementById("err");
    error.textContent = "";

    try {
        const csrfResponse = await fetch("/auth/csrf");
        if (!csrfResponse.ok) throw new Error("Could not initialize secure registration");
        const csrf = await csrfResponse.json();
        const response = await fetch("/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf.token },
            body: JSON.stringify({
                name: document.getElementById("name").value,
                email: document.getElementById("email").value,
                password: document.getElementById("password").value
            })
        });

        if (!response.ok) {
            error.textContent = response.status === 429 ? "Too many registrations. Try again later." : "Registration failed. Check the fields or try another email.";
            return;
        }
        window.location.href = "login.html";
    } catch {
        error.textContent = "Registration is temporarily unavailable. Please try again.";
    }
});
