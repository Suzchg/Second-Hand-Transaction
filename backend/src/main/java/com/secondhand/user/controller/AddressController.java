package com.secondhand.user.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.user.entity.UserAddress;
import com.secondhand.user.service.AddressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<UserAddress>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(addressService.listByUserId(principal.userId()));
    }

    @PostMapping
    public ApiResponse<UserAddress> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AddressRequest req) {
        UserAddress addr = new UserAddress();
        addr.setReceiverName(req.receiverName());
        addr.setReceiverPhone(req.receiverPhone());
        addr.setProvince(req.province());
        addr.setCity(req.city());
        addr.setDistrict(req.district());
        addr.setDetailAddress(req.detailAddress());
        addr.setIsDefault(req.isDefault());
        return ApiResponse.ok(addressService.create(principal.userId(), addr));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAddress> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long addressId,
            @Valid @RequestBody AddressRequest req) {
        UserAddress addr = new UserAddress();
        addr.setReceiverName(req.receiverName());
        addr.setReceiverPhone(req.receiverPhone());
        addr.setProvince(req.province());
        addr.setCity(req.city());
        addr.setDistrict(req.district());
        addr.setDetailAddress(req.detailAddress());
        addr.setIsDefault(req.isDefault());
        return ApiResponse.ok(addressService.update(principal.userId(), addressId, addr));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long addressId) {
        addressService.delete(principal.userId(), addressId);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/default")
    public ApiResponse<UserAddress> setDefault(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long addressId) {
        return ApiResponse.ok(addressService.setDefault(principal.userId(), addressId));
    }

    record AddressRequest(
            @NotBlank String receiverName,
            @NotBlank String receiverPhone,
            @NotBlank String province,
            @NotBlank String city,
            @NotBlank String district,
            @NotBlank String detailAddress,
            Boolean isDefault) {}
}
