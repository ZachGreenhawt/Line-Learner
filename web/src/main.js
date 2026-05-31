const form = document.querySelector("#form");
const out = document.querySelector("#out");

form.addEventListener("submit", async (e) => {
  e.preventDefault();

  out.textContent = "Uploading...";

  const scriptData = new FormData(form);

  const res = await fetch("/api/upload", {
    method: "POST",
    body: scriptData,
  });

  const data = await res.json();

  if (!data.ok) {
    out.textContent = data.err || "Upload failed";
    return;
  }

  out.textContent = JSON.stringify(data, null, 2);
});
