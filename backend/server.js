import express from "express";
import cors from "cors";
import multer from "multer";
import dotenv from "dotenv";
import OpenAI from "openai";
import fs from "fs";

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

if (!process.env.OPENAI_API_KEY) {
  console.warn("WARNING: OPENAI_API_KEY is not configured.");
}

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY
});

app.use(cors());
app.use(express.json({ limit: "2mb" }));

const upload = multer({
  dest: "uploads/",
  limits: {
    fileSize: 20 * 1024 * 1024
  }
});

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "Wayss AI Image Studio"
  });
});

app.post("/api/edit-image", upload.single("image"), async (req, res) => {
  const filePath = req.file?.path;

  try {
    if (!req.file) {
      return res.status(400).json({ error: "Image is required." });
    }

    const prompt = String(req.body?.prompt || "").trim();

    if (!prompt) {
      return res.status(400).json({ error: "Prompt is required." });
    }

    const response = await openai.images.edit({
      model: "gpt-image-2",
      image: fs.createReadStream(filePath),
      prompt: `Edit the supplied image according to this user instruction.

USER INSTRUCTION:
${prompt}

Preserve the main subject and identity unless the user explicitly asks to change them.
Only make requested changes.
Keep composition and details natural unless the prompt asks otherwise.
Honor Hindi, Hinglish, and English instructions.`,
      size: "1024x1024"
    });

    const image = response.data?.[0]?.b64_json;

    if (!image) {
      throw new Error("The image API returned no image data.");
    }

    res.json({
      success: true,
      image
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({
      success: false,
      error: error?.message || "Image editing failed."
    });
  } finally {
    if (filePath && fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
    }
  }
});

app.listen(PORT, () => {
  console.log(`Wayss backend running on port ${PORT}`);
});
