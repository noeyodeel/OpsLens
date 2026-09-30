package com.opslens.api.his;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.his.HisAnalyzeRequest;
import com.opslens.application.his.HisErrorAnalysisResult;
import com.opslens.application.his.HisErrorAnalysisService;
import com.opslens.application.his.HisErrorNotFoundException;
import com.opslens.application.his.HisErrorOperationRecord;
import com.opslens.application.his.HisErrorOperationUpdateRequest;

@RestController
@RequestMapping("/api/his-errors")
public class HisErrorAnalysisController {

    private final HisErrorAnalysisService hisErrorAnalysisService;

    public HisErrorAnalysisController(HisErrorAnalysisService hisErrorAnalysisService) {
        this.hisErrorAnalysisService = hisErrorAnalysisService;
    }

    @PostMapping("/{errorId}/analyze")
    public HisErrorAnalysisResult analyze(@PathVariable long errorId, @RequestBody(required = false) HisAnalyzeRequest request) {
        return hisErrorAnalysisService.analyze(errorId, request == null ? null : request.question());
    }

    @GetMapping("/{errorId}/operation")
    public HisErrorOperationRecord operationRecord(@PathVariable long errorId) {
        return hisErrorAnalysisService.operationRecord(errorId);
    }

    @PutMapping("/{errorId}/operation")
    public HisErrorOperationRecord updateOperationRecord(@PathVariable long errorId, @RequestBody(required = false) HisErrorOperationUpdateRequest request) {
        return hisErrorAnalysisService.updateOperationRecord(errorId, request);
    }

    @ExceptionHandler(HisErrorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }
}
