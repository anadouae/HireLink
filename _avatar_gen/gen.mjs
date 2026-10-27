import { createAvatar } from "@dicebear/core";
import { personas } from "@dicebear/collection";
import { mkdirSync, writeFileSync } from "fs";

const avatars = "C:/Users/daurinia/Desktop/HireLink/frontend/img/avatars";
mkdirSync(avatars, { recursive: true });

const seeds = [
  "Sara", "Youssef", "Amina", "Omar", "Imane", "Karim", "Lina", "Nadia",
  "Mehdi", "Salma", "Adam", "Hajar", "Anas", "Fatima", "Rayan", "Ines",
];
const moods = {
  ok: { eyes: ["happy"], mouth: ["bigSmile"] },
  info: { eyes: ["open"], mouth: ["smile"] },
  warn: { eyes: ["open"], mouth: ["surprise"] },
  danger: { eyes: ["open"], mouth: ["frown"] },
};
seeds.forEach((seed, i) => {
  const n = String(i + 1).padStart(2, "0");
  Object.keys(moods).forEach((mood) => {
    const svg = createAvatar(personas, Object.assign({ seed }, moods[mood])).toString();
    writeFileSync(`${avatars}/p${n}-${mood}.svg`, svg);
  });
});
console.log("personas moods ok");
