# Automatic Document Classification

Automatically rename and move documents to a folder structure based on their content using OCR/AI-powered analysis.

- 📄 Supports Text, PDF and image files (PNG, JPG)
- 🔍 OCR text extraction with Tesseract (via Docker) or vision AI (Ollama)
- 🤖 AI-powered content analysis and classification
- 📂 Intelligent file organization based on present document structure
- 📊 Progress tracking and time statistics

## How It Works

![Process overview diagram](doc/classification-process.drawio.svg)

1. **Text Extraction**
    - PDFs are converted to images and processed with OCR
    - Direct text extraction for text-based files
    - OCR results are cleaned and truncated for AI processing

2. **AI Analysis**
    - Document summary generation
    - Context-aware filename suggestion
    - Directory path recommendation based on content

3. **File Organization**
    - Automatic file relocation with conflict resolution
    - Directory structure validation and creation
    - Clean filename generation with special character removal

I achieved processing times of roughly one document per minute, on my NVIDIA GeForce RTX 3090,
using with the `deepseek-r1` model.
I can't scan and sort my documents physically very much faster than that, so I'm happy with the performance.
This long processing time comes from the three different steps I split the process into.
I couldn't get the results I wanted with fewer prompts.

The tesseract OCR is MUCH cheaper than the AI, but the AI is much more accurate.
As usual, trade-offs.
You can configure the tool to use either one via the `ocr.method` property in the config file.

Example output:

```bash
DOCUMENT CLASSIFIER - 1 file - 2025-01-28

┌── [01 / 01] ─────────────────────────────────────────────────────────────────
│ IMG_20250128_0001.pdf
│ [  2.95s] Extracted text:      519 chars
│ [ 48.13s] Document summarized: 1189 chars
│ [ 19.68s] Filename generated:  2024-12-30 Kassenbeleg DEICHMANN SE 0000154301000 EUR 20,98.pdf
│ [ 20.37s] Path generated:      05 Rechnungen, Angebote/Rechnungen
│ [ 91.16s] Moved file:          05 Rechnungen, Angebote/Rechnungen/2024-12-30 Kassenbeleg DEICHMANN SE 0000154301000 EUR 2098.pdf
└──────────────────────────────────────────────────────────────────────────────
┌──────────────────────────────────────────────────────────────────────────────
│ [ 91.18s] Finished classification: 1 file processed
└──────────────────────────────────────────────────────────────────────────────
```

## Usage

### Prerequisites

- Java 17+
- [Maven 3.8+](https://maven.apache.org/download.cgi)
- [Docker](https://www.docker.com/get-started) (if you use Tesseract OCR)
- Docker image: [tesseractshadow/tesseract4re](https://hub.docker.com/r/tesseractshadow/tesseract4re/) (if you use Tesseract OCR)
- [Ollama](https://ollama.ai/) or compatible AI API endpoint (OpenAI for example), but I would really recommend going
  for a locally hosted solution, because you usually don't want your documents to be shared with a third party.
  And with the new `deepseek-r1` model, locally hosted AI has reached a competitive level to big tech.
  Obviously this will also work with a cheaper model than `deepseek-r1`, but I haven't tested it on a larger scale with
  any other model yet.

```shell
# either tesseract or a vision model is required
docker pull tesseractshadow/tesseract4re
ollama run llama3.2-vision
# other models
ollama run deepseek-r1:32b
# run the server
ollama serve
```

### Build Instructions

Configure [src/main/resources/config.properties](src/main/resources/config.properties) with the necessary settings.

| Property                     | Description                                                                                                                  |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `ai.chat.baseurl`            | Ollama API endpoint URL                                                                                                      |
| `ai.chat.model`              | AI model to use (e.g., deepseek-r1:32b)                                                                                      |
| `ai.image.model`             | AI model to use for image content analysis. Only used if AI image OCR is configured. (e.g., llama3.2-vision)                 |
| `documents.refdir.basepath`  | Reference directory for directory file sorting                                                                               |
| `documents.movedir.basepath` | Target directory for organized files (kept separately to not break existing structure with incorrectly classified documents) |
| `ocr.language`               | Preferred Tesseract OCR language code (e.g., eng, deu)                                                                       |
| `ocr.method`                 | Either `tesseract` or `ollama`. Tesseract uses a Docker container, Ollama uses the Ollama API and the `ai.image.model`       |

Then run:

```shell
mvn clean package
```

#### Create run scripts, adjust your paths to the jar file

Windows (`run.bat`):

```bat
@echo off
java -jar target\automatic-document-classification-1.0-SNAPSHOT.jar %*
pause
```

Linux/Mac (`run.sh`):

```bash
#!/bin/bash
java -jar target/automatic-document-classification-1.0-SNAPSHOT.jar "$@"
```

You can then trigger the processing of documents by dragging their files or entire folders onto the script files.

### Adjust behavior

#### Directory Structure

- Maintain a `.docinfo` file in directories to provide context
  (currently unused, but can be added by [changing the prompts](src/main/resources/chat) to use the variable
  `[[docfiles]]`)
- Use `.docignore` to exclude directories from scanning

#### Adjust prompts directly

I made the model follow my specific needs for document naming schemes.
Feel free to [change the prompts](src/main/resources/chat) in the resources folder to fit your needs.

## License

This project is licensed under the Apache License Version 2.0 - see the [LICENSE](LICENSE) file for details.
Generally, do whatever you want with the project, I'm happy if it helps you in any way.
