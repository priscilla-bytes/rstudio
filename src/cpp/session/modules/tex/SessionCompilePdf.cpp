/*
 * SessionCompilePdf.cpp
 *
 * Copyright (C) 2009-11 by RStudio, Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#include "SessionCompilePdf.hpp"

#include <boost/format.hpp>
#include <boost/foreach.hpp>
#include <boost/enable_shared_from_this.hpp>

#include <core/FilePath.hpp>
#include <core/Exec.hpp>
#include <core/FileSerializer.hpp>
#include <core/system/ShellUtils.hpp>

#include <core/tex/TexLogParser.hpp>
#include <core/tex/TexMagicComment.hpp>

#include <r/RSexp.hpp>
#include <r/RExec.hpp>
#include <r/RRoutines.hpp>

#include <session/SessionUserSettings.hpp>
#include <session/SessionModuleContext.hpp>

#include "SessionPdfLatex.hpp"
#include "SessionTexi2Dvi.hpp"
#include "SessionRnwWeave.hpp"
#include "SessionRnwConcordance.hpp"
#include "SessionCompilePdfSupervisor.hpp"

// TODO: can't seem to get bibtex errors to show up -- test this

// TODO: pull from find in files branch

// TODO: now that compilePdf is fully async, why should it even
// be a function call? (why not a command handled by the presenter)

// TODO: texi2dvi script prints to stderr when rstudio-pdflatex fails
// with an error code, prevent this

// TODO: fully mask shared_ptr/start for PdfCompiler (AysncPdfCompiler)

// TODO: deal with ClientState

// TODO: clear output before new compile

// TODO: don't allow multiple concurrent compilations

// TOOD: perhaps diable closeabilty if running?

// TODO: auto-bring to front on start but not on subsequent output

// TODO: ability to stop/interrupt

// TODO: buffer output on server (devmode perf)

// TOOD: don't grab focus (blinking cursor) -- perhaps just use
// VirtualConsole + PreWidget?


using namespace core;

namespace session {
namespace modules { 
namespace tex {
namespace compile_pdf {

namespace {

void viewPdf(const FilePath& texPath)
{
   FilePath pdfPath = texPath.parent().complete(texPath.stem() + ".pdf");
   module_context::showFile(pdfPath, "_rstudio_compile_pdf");
}

void publishPdf(const FilePath& texPath)
{
   std::string aliasedPath = module_context::createAliasedPath(texPath);
   ClientEvent event(client_events::kPublishPdf, aliasedPath);
   module_context::enqueClientEvent(event);
}

json::Object logEntryJson(const core::tex::LogEntry& logEntry)
{
   json::Object obj;
   obj["type"] = static_cast<int>(logEntry.type());
   obj["file"] = logEntry.file();
   obj["line"] = logEntry.line();
   obj["message"] = logEntry.message();
   return obj;
}

void showLogEntries(const core::tex::LogEntries& logEntries,
                    const rnw_concordance::Concordance& rnwConcordance =
                                             rnw_concordance::Concordance())
{
   json::Array logEntriesJson;
   BOOST_FOREACH(const core::tex::LogEntry& logEntry, logEntries)
   {
      if (!rnwConcordance.empty() &&
          (rnwConcordance.outputFile() == logEntry.file()))
      {
         core::tex::LogEntry rnwEntry(logEntry.type(),
                                      rnwConcordance.inputFile(),
                                      rnwConcordance.rnwLine(logEntry.line()),
                                      logEntry.message());

         logEntriesJson.push_back(logEntryJson(rnwEntry));
      }
      else
      {
         logEntriesJson.push_back(logEntryJson(logEntry));
      }
   }

   ClientEvent event(client_events::kCompilePdfErrorsEvent, logEntriesJson);
   module_context::enqueClientEvent(event);
}

FilePath ancillaryFilePath(const FilePath& texFilePath, const std::string& ext)
{
   return texFilePath.parent().childPath(texFilePath.stem() + ext);
}

FilePath latexLogPath(const FilePath& texFilePath)
{
   return ancillaryFilePath(texFilePath, ".log");
}

FilePath bibtexLogPath(const FilePath& texFilePath)
{
   return ancillaryFilePath(texFilePath, ".blg");
}

bool showCompilationErrors(const FilePath& texPath,
                           const rnw_concordance::Concordance& rnwConcordance)
{
   // latex log file
   core::tex::LogEntries logEntries;
   FilePath logPath = latexLogPath(texPath);
   if (logPath.exists())
   {
      Error error = core::tex::parseLatexLog(logPath, &logEntries);
      if (error)
         LOG_ERROR(error);
   }

   // bibtex log file
   core::tex::LogEntries bibtexLogEntries;
   logPath = bibtexLogPath(texPath);
   if (logPath.exists())
   {
      Error error = core::tex::parseBibtexLog(logPath, &bibtexLogEntries);
      if (error)
         LOG_ERROR(error);
   }

   // concatenate them together
   std::copy(bibtexLogEntries.begin(),
             bibtexLogEntries.end(),
             std::back_inserter(logEntries));

   // show errors if necessary
   if (!logEntries.empty())
   {
      showLogEntries(logEntries, rnwConcordance);
      return true;
   }
   else
   {
      return false;
   }
}

void removeExistingLogs(const FilePath& texFilePath)
{
   Error error = latexLogPath(texFilePath).removeIfExists();
   if (error)
      LOG_ERROR(error);

   error = bibtexLogPath(texFilePath).removeIfExists();
   if (error)
      LOG_ERROR(error);
}

class AuxillaryFileCleanupContext : boost::noncopyable
{
public:
   AuxillaryFileCleanupContext()
      : cleanLog_(true)
   {
   }

   virtual ~AuxillaryFileCleanupContext()
   {
      try
      {
         cleanup();
      }
      catch(...)
      {
      }
   }

   void init(const FilePath& targetFilePath)
   {
      basePath_ = targetFilePath.parent().childPath(
                                    targetFilePath.stem()).absolutePath();
   }

   void preserveLog()
   {
      cleanLog_ = false;
   }

   void cleanup()
   {
      if (!basePath_.empty())
      {
         // remove known auxillary files
         remove(".out");
         remove(".aux");


         // only clean bbl if .bib exists
         if (exists(".bib"))
            remove(".bbl");

         // clean log if requested
         if (cleanLog_)
         {
            remove(".blg");
            remove(".log");
         }

         // reset base path so we only do this one
         basePath_.clear();
      }
   }

private:
   bool exists(const std::string& extension)
   {
      return FilePath(basePath_ + extension).exists();
   }

   void remove(const std::string& extension)
   {
      Error error = FilePath(basePath_ + extension).removeIfExists();
      if (error)
         LOG_ERROR(error);
   }

private:
   std::string basePath_;
   bool cleanLog_;
};

// implement pdf compilation within a class so we can maintain state
// accross the various async callbacks the compile is composed of
class PdfCompiler : boost::noncopyable,
                    public boost::enable_shared_from_this<PdfCompiler>
{
public:
   static boost::shared_ptr<PdfCompiler> create(
                              const FilePath& targetFilePath,
                              const boost::function<void()>& onCompleted)
   {
      return boost::shared_ptr<PdfCompiler>(new PdfCompiler(targetFilePath,
                                                            onCompleted));

   }

   virtual ~PdfCompiler() {}

private:
   PdfCompiler(const FilePath& targetFilePath,
               const boost::function<void()>& onCompleted)
      : targetFilePath_(targetFilePath), onCompleted_(onCompleted)
   {
   }

public:
   void start()
   {
      // ensure no spaces in path
      std::string filename = targetFilePath_.filename();
      if (filename.find(' ') != std::string::npos)
      {
         reportError("Invalid filename: '" + filename +
                     "' (TeX does not understand paths with spaces)");
         return;
      }

      // parse magic comments
      Error error = core::tex::parseMagicComments(targetFilePath_,
                                                  &magicComments_);
      if (error)
         LOG_ERROR(error);

      // determine tex program path
      std::string userErrMsg;
      if (!pdflatex::latexProgramForFile(magicComments_,
                                         &texProgramPath_,
                                         &userErrMsg))
      {
         reportError(userErrMsg);
         return;
      }

      // see if we need to weave
      std::string ext = targetFilePath_.extensionLowerCase();
      bool isRnw = ext == ".rnw" || ext == ".snw" || ext == ".nw";
      if (isRnw)
      {
         // attempt to weave the rnw
         rnw_weave::runWeave(targetFilePath_,
                             magicComments_,
                             boost::bind(&PdfCompiler::onWeaveCompleted,
                                      PdfCompiler::shared_from_this(), _1));
      }
      else
      {
         runLatexCompiler();
      }

   }

private:

   void onWeaveCompleted(const rnw_weave::Result& result)
   {
      if (result.succeeded)
         runLatexCompiler(result.concordance);
      else
         reportError(result.errorMessage);
   }

   void runLatexCompiler(const rnw_concordance::Concordance& concordance =
                                                rnw_concordance::Concordance())
   {
      // configure pdflatex options
      pdflatex::PdfLatexOptions options;
      options.fileLineError = true;
      options.syncTex = true;
      options.shellEscape = userSettings().enableLaTeXShellEscape();

      // get back-end version info
      core::system::ProcessResult result;
      Error error = core::system::runProgram(
                  string_utils::utf8ToSystem(texProgramPath_.absolutePath()),
                  core::shell_utils::ShellArgs() << "--version",
                  "",
                  core::system::ProcessOptions(),
                  &result);
      if (error)
         LOG_ERROR(error);
      else if (result.exitStatus != EXIT_SUCCESS)
         LOG_ERROR_MESSAGE("Error probing for latex version: "+ result.stdErr);
      else
         options.versionInfo = result.stdOut;

      // compute tex file path
      FilePath texFilePath = targetFilePath_.parent().complete(
                                                targetFilePath_.stem() +
                                                ".tex");

      // remove log files if they exist (avoids confusion created by parsing
      // old log files for errors)
      removeExistingLogs(texFilePath);

      // setup cleanup context if clean was specified
      if (userSettings().cleanTexi2DviOutput())
         auxillaryFileCleanupContext_.init(texFilePath);

      // run latex compile
      compile_pdf_supervisor::showOutput("\nRunning LaTeX compiler...");

      // try to use texi2dvi if we can
      if (userSettings().useTexi2Dvi() && tex::texi2dvi::isAvailable())
      {
         Error error = tex::texi2dvi::texToPdf(
                           texProgramPath_,
                           texFilePath,
                           options,
                           boost::bind(&PdfCompiler::onLatexCompileCompleted,
                                          PdfCompiler::shared_from_this(),
                                          _1,
                                          texFilePath,
                                          concordance));
         if (error)
            reportError("Unable to compile pdf: " + error.summary());
      }

      // call pdflatex directly (but still try to run bibtex as necessary)
      else
      {
         // this is our "simulated" texi2dvi -- this was originally
         // coded as a sequence of sync calls to pdflatex, bibtex, and
         // makeindex. re-coding it as async is going to be a bit
         // involved so considering that this is not the default
         // codepath we'll leave it sync for now (and then just call
         // the (typically) async callback function onLatexCompileCompleted
         // directly after the function returns

         Error error = tex::pdflatex::texToPdf(texProgramPath_,
                                               texFilePath,
                                               options,
                                               &result);

         if (error)
         {
            reportError("Unable to compile pdf: " + error.summary());
         }
         else
         {
            onLatexCompileCompleted(result.exitStatus,
                                    texFilePath,
                                    concordance);
         }
      }
   }

   void onLatexCompileCompleted(int exitStatus,
                                const FilePath& texFilePath,
                                const rnw_concordance::Concordance& concord)
   {
      if (exitStatus == EXIT_SUCCESS)
      {
         compile_pdf_supervisor::showOutput("completed\n");

         if (onCompleted_)
            onCompleted_();
      }
      else
      {
         compile_pdf_supervisor::showOutput("\n");

         // don't remove the log
         auxillaryFileCleanupContext_.preserveLog();

         // try to show compilation errors -- if none are found then print
         // a general error message and stderr
         if (!showCompilationErrors(texFilePath, concord))
         {
            boost::format fmt("Error running %1% (exit code %2%)");
            std::string msg(boost::str(fmt % texProgramPath_.absolutePath()
                                           % exitStatus));
            reportError(msg);
         }
      }
   }

   void reportError(const std::string& message)
   {
      compile_pdf_supervisor::showOutput(message + "\n");
   }

private:
   const FilePath targetFilePath_;
   const boost::function<void()> onCompleted_;
   core::tex::TexMagicComments magicComments_;
   FilePath texProgramPath_;
   AuxillaryFileCleanupContext auxillaryFileCleanupContext_;
};



SEXP rs_compilePdf(SEXP filePathSEXP, SEXP completedActionSEXP)
{
   try
   {
      // get target file path
      FilePath targetFilePath = module_context::resolveAliasedPath(
                                          r::sexp::asString(filePathSEXP));

      // initialize completed function
      std::string completedAction = r::sexp::asString(completedActionSEXP);
      boost::function<void()> completedFunction;
      if (completedAction == "view")
         completedFunction = boost::bind(viewPdf, targetFilePath);
      else if (completedAction == "publish")
         completedFunction = boost::bind(publishPdf, targetFilePath);


      // compile pdf
      boost::shared_ptr<PdfCompiler> pCompiler =
                        PdfCompiler::create(targetFilePath, completedFunction);
      pCompiler->start();
   }
   CATCH_UNEXPECTED_EXCEPTION

   return R_NilValue;
}


} // anonymous namespace


Error initialize()
{
   R_CallMethodDef compilePdfMethodDef;
   compilePdfMethodDef.name = "rs_compilePdf" ;
   compilePdfMethodDef.fun = (DL_FUNC) rs_compilePdf ;
   compilePdfMethodDef.numArgs = 2;
   r::routines::addCallMethod(compilePdfMethodDef);

   using boost::bind;
   using namespace module_context;
   ExecBlock initBlock ;
   initBlock.addFunctions()
      (compile_pdf_supervisor::initialize)
      (bind(sourceModuleRFile, "SessionCompilePdf.R"));
   return initBlock.execute();

}


} // namespace compile_pdf
} // namespace tex
} // namespace modules
} // namesapce session

