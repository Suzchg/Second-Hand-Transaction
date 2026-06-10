package com.secondhand.user.service;

import com.secondhand.common.AppException;
import com.secondhand.user.entity.UserAddress;
import com.secondhand.user.repository.UserAddressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressService {

    private final UserAddressRepository addressRepo;

    public AddressService(UserAddressRepository addressRepo) {
        this.addressRepo = addressRepo;
    }

    @Transactional(readOnly = true)
    public List<UserAddress> listByUserId(Long userId) {
        return addressRepo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    @Transactional
    public UserAddress create(Long userId, UserAddress address) {
        address.setUserId(userId);
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            addressRepo.clearDefaultByUserId(userId);
        }
        // 如果是第一个地址，自动设为默认
        if (addressRepo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).isEmpty()) {
            address.setIsDefault(true);
        }
        return addressRepo.save(address);
    }

    @Transactional
    public UserAddress update(Long userId, Long addressId, UserAddress updated) {
        UserAddress addr = addressRepo.findById(addressId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "地址不存在", HttpStatus.NOT_FOUND));
        if (!addr.getUserId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权修改该地址", HttpStatus.FORBIDDEN);
        }
        if (updated.getReceiverName() != null) addr.setReceiverName(updated.getReceiverName());
        if (updated.getReceiverPhone() != null) addr.setReceiverPhone(updated.getReceiverPhone());
        if (updated.getProvince() != null) addr.setProvince(updated.getProvince());
        if (updated.getCity() != null) addr.setCity(updated.getCity());
        if (updated.getDistrict() != null) addr.setDistrict(updated.getDistrict());
        if (updated.getDetailAddress() != null) addr.setDetailAddress(updated.getDetailAddress());
        if (updated.getIsDefault() != null) {
            if (Boolean.TRUE.equals(updated.getIsDefault())) {
                addressRepo.clearDefaultByUserId(userId);
            }
            addr.setIsDefault(updated.getIsDefault());
        }
        if (updated.getTag() != null) addr.setTag(updated.getTag());
        addr.setUpdatedAt(LocalDateTime.now());
        return addressRepo.save(addr);
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        UserAddress addr = addressRepo.findById(addressId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "地址不存在", HttpStatus.NOT_FOUND));
        if (!addr.getUserId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权删除该地址", HttpStatus.FORBIDDEN);
        }
        addressRepo.delete(addr);
    }

    @Transactional
    public UserAddress setDefault(Long userId, Long addressId) {
        UserAddress addr = addressRepo.findById(addressId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "地址不存在", HttpStatus.NOT_FOUND));
        if (!addr.getUserId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权操作该地址", HttpStatus.FORBIDDEN);
        }
        addressRepo.clearDefaultByUserId(userId);
        addr.setIsDefault(true);
        addr.setUpdatedAt(LocalDateTime.now());
        return addressRepo.save(addr);
    }
}
