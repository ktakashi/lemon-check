package io.github.ktakashi.samples.petstore.controller;

import io.github.ktakashi.samples.petstore.dto.ErrorResponse;
import io.github.ktakashi.samples.petstore.dto.NewPet;
import io.github.ktakashi.samples.petstore.dto.PetResponse;
import io.github.ktakashi.samples.petstore.service.PetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for pet operations.
 * 
 * This controller is mounted at /api/v1/pets to demonstrate multi-host API testing.
 * The petstore API uses /api/v1 while auth API uses /auth/api/v1, allowing us to
 * test with different base URLs.
 */
@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    /**
     * List all pets.
     * GET /pets?limit=20&status=available
     */
    @GetMapping
    public ResponseEntity<?> listPets(
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false) String status) {
        
        // Validate limit
        if (limit != null && (limit < 1 || limit > 100)) {
            return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Invalid limit value", 
                    List.of("limit must be between 1 and 100")));
        }

        // Validate status
        if (status != null && !status.isBlank()) {
            if (!isValidStatus(status)) {
                return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(400, "Invalid status value",
                        List.of("status must be one of: available, pending, sold")));
            }
        }

        List<PetResponse> pets = petService.listPets(limit, status);
        long total = petService.countPets(status);

        return ResponseEntity.ok(Map.of(
            "pets", pets,
            "total", total
        ));
    }

    /**
     * Get a pet by ID.
     * GET /pets/{petId}
     */
    @GetMapping("/{petId}")
    public ResponseEntity<?> getPetById(@PathVariable Long petId) {
        return petService.getPetById(petId)
            .map(pet -> ResponseEntity.ok((Object) pet))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Pet not found",
                    List.of("No pet exists with id: " + petId))));
    }

    /**
     * Create a new pet.
     * POST /pets
     */
    @PostMapping
    public ResponseEntity<?> createPet(@Valid @RequestBody NewPet newPet) {
        // Validate status if provided
        if (newPet.status() != null && !newPet.status().isBlank() && !isValidStatus(newPet.status())) {
            return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Validation failed",
                    List.of("status must be one of: available, pending, sold")));
        }

        PetResponse created = petService.createPet(newPet);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update an existing pet.
     * PUT /pets/{petId}
     */
    @PutMapping("/{petId}")
    public ResponseEntity<?> updatePet(
            @PathVariable Long petId,
            @Valid @RequestBody NewPet newPet) {
        
        // Validate status if provided
        if (newPet.status() != null && !newPet.status().isBlank() && !isValidStatus(newPet.status())) {
            return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Validation failed",
                    List.of("status must be one of: available, pending, sold")));
        }

        return petService.updatePet(petId, newPet)
            .map(pet -> ResponseEntity.ok((Object) pet))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Pet not found",
                    List.of("No pet exists with id: " + petId))));
    }

    /**
     * Delete a pet.
     * DELETE /pets/{petId}
     */
    @DeleteMapping("/{petId}")
    public ResponseEntity<?> deletePet(@PathVariable Long petId) {
        if (petService.deletePet(petId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(404, "Pet not found",
                List.of("No pet exists with id: " + petId)));
    }

    private boolean isValidStatus(String status) {
        return status.equalsIgnoreCase("available") ||
               status.equalsIgnoreCase("pending") ||
               status.equalsIgnoreCase("sold");
    }
}
